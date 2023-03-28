/*
 * Copyright The Cryostat Authors
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.cryostat.net;

import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

import javax.management.ObjectName;
import javax.script.ScriptException;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.common.unit.IConstraint;
import org.openjdk.jmc.common.unit.IOptionDescriptor;
import org.openjdk.jmc.common.unit.IQuantity;
import org.openjdk.jmc.common.unit.QuantityConversionException;
import org.openjdk.jmc.common.unit.SimpleConstrainedMap;
import org.openjdk.jmc.common.unit.UnitLookup;
import org.openjdk.jmc.flightrecorder.configuration.events.EventOptionID;
import org.openjdk.jmc.flightrecorder.configuration.events.IEventTypeID;
import org.openjdk.jmc.flightrecorder.configuration.internal.EventTypeIDV2;
import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.core.net.MBeanMetrics;
import io.cryostat.util.HttpStatusCodeIdentifier;

import com.google.gson.Gson;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.authentication.UsernamePasswordCredentials;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.auth.InvalidCredentialsException;

public class AgentClient {
    public static final String NULL_CREDENTIALS = "No credentials found for agent";

    private final Vertx vertx;
    private final Gson gson;
    private final long httpTimeout;
    private final WebClient webClient;
    private final CredentialsManager credentialsManager;
    private final URI agentUri;
    private final Logger logger;

    AgentClient(
            Vertx vertx,
            Gson gson,
            long httpTimeout,
            WebClient webClient,
            CredentialsManager credentialsManager,
            URI agentUri,
            Logger logger) {
        this.vertx = vertx;
        this.gson = gson;
        this.httpTimeout = httpTimeout;
        this.webClient = webClient;
        this.credentialsManager = credentialsManager;
        this.agentUri = agentUri;
        this.logger = logger;
    }

    URI getUri() {
        return agentUri;
    }

    Future<Boolean> ping() {
        Future<HttpResponse<Void>> f = invoke(HttpMethod.GET, "/", BodyCodec.none());
        return f.map(HttpResponse::statusCode).map(HttpStatusCodeIdentifier::isSuccessCode);
    }

    Future<MBeanMetrics> mbeanMetrics() {
        Future<HttpResponse<String>> f =
                invoke(HttpMethod.GET, "/mbean-metrics", BodyCodec.string());
        return f.map(HttpResponse::body)
                // uses Gson rather than Vertx's Jackson because Gson is able to handle MBeanMetrics
                // with no additional fuss. Jackson complains about private final fields.
                .map(s -> gson.fromJson(s, MBeanMetrics.class));
    }

    Future<List<IRecordingDescriptor>> activeRecordings() {
        Future<HttpResponse<JsonArray>> f =
                invoke(HttpMethod.GET, "/recordings", BodyCodec.jsonArray());
        return f.map(HttpResponse::body)
                .map(
                        arr ->
                                arr.stream()
                                        .map(
                                                o ->
                                                        (IRecordingDescriptor)
                                                                new AgentRecordingDescriptor(
                                                                        (JsonObject) o))
                                        .toList());
    }

    Future<Collection<? extends IEventTypeInfo>> eventTypes() {
        Future<HttpResponse<JsonArray>> f =
                invoke(HttpMethod.GET, "/event-types", BodyCodec.jsonArray());
        return f.map(HttpResponse::body)
                .map(arr -> arr.stream().map(o -> new AgentEventTypeInfo((JsonObject) o)).toList());
    }

    Future<IConstrainedMap<EventOptionID>> eventSettings() {
        Future<HttpResponse<JsonArray>> f =
                invoke(HttpMethod.GET, "/event-settings", BodyCodec.jsonArray());
        return f.map(HttpResponse::body)
                .map(
                        arr -> {
                            return arr.stream()
                                    .map(
                                            o -> {
                                                JsonObject json = (JsonObject) o;
                                                String eventName = json.getString("name");
                                                JsonArray jsonSettings =
                                                        json.getJsonArray("settings");
                                                Map<String, String> settings = new HashMap<>();
                                                jsonSettings.forEach(
                                                        s -> {
                                                            JsonObject j = (JsonObject) s;
                                                            settings.put(
                                                                    j.getString("name"),
                                                                    j.getString("defaultValue"));
                                                        });
                                                return Pair.of(eventName, settings);
                                            })
                                    .toList();
                        })
                .map(
                        list -> {
                            SimpleConstrainedMap<EventOptionID> result =
                                    new SimpleConstrainedMap<EventOptionID>(null);
                            list.forEach(
                                    item -> {
                                        item.getRight()
                                                .forEach(
                                                        (key, val) -> {
                                                            try {
                                                                result.put(
                                                                        new EventOptionID(
                                                                                new EventTypeIDV2(
                                                                                        item
                                                                                                .getLeft()),
                                                                                key),
                                                                        null,
                                                                        val);
                                                            } catch (
                                                                    QuantityConversionException
                                                                            qce) {
                                                                logger.warn(qce);
                                                            }
                                                        });
                                    });
                            return result;
                        });
    }

    Future<List<String>> eventTemplates() {
        Future<HttpResponse<JsonArray>> f =
                invoke(HttpMethod.GET, "/event-templates", BodyCodec.jsonArray());
        return f.map(HttpResponse::body).map(arr -> arr.stream().map(Object::toString).toList());
    }

    private <T> Future<HttpResponse<T>> invoke(HttpMethod mtd, String path, BodyCodec<T> codec) {
        return Future.fromCompletionStage(
                CompletableFuture.supplyAsync(
                                () -> {
                                    logger.info("{} {} {}", mtd, agentUri, path);
                                    HttpRequest<T> req =
                                            webClient
                                                    .request(
                                                            mtd,
                                                            agentUri.getPort(),
                                                            agentUri.getHost(),
                                                            path)
                                                    .ssl("https".equals(agentUri.getScheme()))
                                                    .timeout(
                                                            Duration.ofSeconds(httpTimeout)
                                                                    .toMillis())
                                                    .followRedirects(true)
                                                    .as(codec);
                                    try {
                                        Credentials credentials =
                                                credentialsManager.getCredentialsByTargetId(
                                                        agentUri.toString());
                                        if (credentials == null
                                                || credentials.getUsername() == null
                                                || credentials.getPassword() == null) {
                                            throw new InvalidCredentialsException(
                                                    NULL_CREDENTIALS + " " + agentUri);
                                        }
                                        req =
                                                req.authentication(
                                                        new UsernamePasswordCredentials(
                                                                credentials.getUsername(),
                                                                credentials.getPassword()));
                                    } catch (ScriptException | InvalidCredentialsException e) {
                                        logger.error(e);
                                        throw new RuntimeException(e);
                                    }

                                    try {
                                        return req.send()
                                                .toCompletionStage()
                                                .toCompletableFuture()
                                                .get();
                                    } catch (InterruptedException | ExecutionException e) {
                                        logger.error(e);
                                        throw new RuntimeException(e);
                                    }
                                },
                                ForkJoinPool.commonPool())
                        .exceptionally(
                                t -> {
                                    throw new RuntimeException(t);
                                }));
    }

    static class Factory {

        private final Vertx vertx;
        private final Gson gson;
        private final long httpTimeout;
        private final WebClient webClient;
        private final CredentialsManager credentialsManager;
        private final Logger logger;

        Factory(
                Vertx vertx,
                Gson gson,
                long httpTimeout,
                WebClient webClient,
                CredentialsManager credentialsManager,
                Logger logger) {
            this.vertx = vertx;
            this.gson = gson;
            this.httpTimeout = httpTimeout;
            this.webClient = webClient;
            this.credentialsManager = credentialsManager;
            this.logger = logger;
        }

        AgentClient create(URI agentUri) {
            return new AgentClient(
                    vertx, gson, httpTimeout, webClient, credentialsManager, agentUri, logger);
        }
    }

    private static class AgentRecordingDescriptor implements IRecordingDescriptor {

        final JsonObject json;

        AgentRecordingDescriptor(JsonObject json) {
            this.json = json;
        }

        @Override
        public IQuantity getDataStartTime() {
            return getStartTime();
        }

        @Override
        public IQuantity getDataEndTime() {
            if (isContinuous()) {
                return UnitLookup.EPOCH_MS.quantity(0);
            }
            return getDataStartTime().add(getDuration());
        }

        @Override
        public IQuantity getDuration() {
            return UnitLookup.MILLISECOND.quantity(json.getLong("duration"));
        }

        @Override
        public Long getId() {
            return json.getLong("id");
        }

        @Override
        public IQuantity getMaxAge() {
            return UnitLookup.MILLISECOND.quantity(json.getLong("maxAge"));
        }

        @Override
        public IQuantity getMaxSize() {
            return UnitLookup.BYTE.quantity(json.getLong("maxSize"));
        }

        @Override
        public String getName() {
            return json.getString("name");
        }

        @Override
        public ObjectName getObjectName() {
            return null;
        }

        @Override
        public Map<String, ?> getOptions() {
            return json.getJsonObject("options").getMap();
        }

        @Override
        public IQuantity getStartTime() {
            return UnitLookup.EPOCH_MS.quantity(json.getLong("startTime"));
        }

        @Override
        public RecordingState getState() {
            // avoid using Enum.valueOf() since that throws an exception if the name isn't part of
            // the type, and it's nicer to not throw and catch exceptions
            String state = json.getString("state");
            switch (state) {
                case "CREATED":
                    return RecordingState.CREATED;
                case "RUNNING":
                    return RecordingState.RUNNING;
                case "STOPPING":
                    return RecordingState.STOPPING;
                case "STOPPED":
                    return RecordingState.STOPPED;
                default:
                    return RecordingState.RUNNING;
            }
        }

        @Override
        public boolean getToDisk() {
            return json.getBoolean("toDisk");
        }

        @Override
        public boolean isContinuous() {
            return json.getBoolean("isContinuous");
        }
    }

    private static class AgentEventTypeInfo implements IEventTypeInfo {

        final JsonObject json;

        AgentEventTypeInfo(JsonObject json) {
            this.json = json;
        }

        @Override
        public String getDescription() {
            return json.getString("description");
        }

        @Override
        public IEventTypeID getEventTypeID() {
            return new EventTypeIDV2(json.getString("name"));
        }

        @Override
        public String[] getHierarchicalCategory() {
            return ((List<String>)
                            json.getJsonArray("categories").getList().stream()
                                    .map(Object::toString)
                                    .toList())
                    .toArray(new String[0]);
        }

        @Override
        public String getName() {
            return json.getString("name");
        }

        static <T, V> V capture(T t) {
            // TODO clean up this generics hack
            return (V) t;
        }

        @Override
        public Map<String, ? extends IOptionDescriptor<?>> getOptionDescriptors() {
            Map<String, ? extends IOptionDescriptor<?>> result = new HashMap<>();
            JsonArray settings = json.getJsonArray("settings");
            settings.forEach(
                    setting -> {
                        String name = ((JsonObject) setting).getString("name");
                        String defaultValue = ((JsonObject) setting).getString("defaultValue");
                        result.put(
                                name,
                                capture(
                                        new IOptionDescriptor<String>() {
                                            @Override
                                            public String getName() {
                                                return name;
                                            }

                                            @Override
                                            public String getDescription() {
                                                return null;
                                            }

                                            @Override
                                            public IConstraint<String> getConstraint() {
                                                return null;
                                            }

                                            @Override
                                            public String getDefault() {
                                                return defaultValue;
                                            }
                                        }));
                    });
            return result;
        }

        @Override
        public IOptionDescriptor<?> getOptionInfo(String s) {
            return getOptionDescriptors().get(s);
        }
    }
}
