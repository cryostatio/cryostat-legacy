/*
 * Copyright The Cryostat Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cryostat.net;

import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import javax.script.ScriptException;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.common.unit.IConstraint;
import org.openjdk.jmc.common.unit.IMutableConstrainedMap;
import org.openjdk.jmc.common.unit.IOptionDescriptor;
import org.openjdk.jmc.common.unit.QuantityConversionException;
import org.openjdk.jmc.common.unit.SimpleConstrainedMap;
import org.openjdk.jmc.flightrecorder.configuration.events.EventOptionID;
import org.openjdk.jmc.flightrecorder.configuration.events.IEventTypeID;
import org.openjdk.jmc.flightrecorder.configuration.internal.EventTypeIDV2;
import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.core.net.MBeanMetrics;
import io.cryostat.core.serialization.SerializableRecordingDescriptor;
import io.cryostat.net.AgentJFRService.StartRecordingRequest;
import io.cryostat.util.HttpStatusCodeIdentifier;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.authentication.UsernamePasswordCredentials;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import jdk.jfr.RecordingState;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.auth.InvalidCredentialsException;

public class AgentClient {
    public static final String NULL_CREDENTIALS = "No credentials found for agent";

    private final ExecutorService executor;
    private final Gson gson;
    private final long httpTimeout;
    private final WebClient webClient;
    private final CredentialsManager credentialsManager;
    private final URI agentUri;
    private final Logger logger;

    AgentClient(
            ExecutorService executor,
            Gson gson,
            long httpTimeout,
            WebClient webClient,
            CredentialsManager credentialsManager,
            URI agentUri,
            Logger logger) {
        this.executor = executor;
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
                invoke(HttpMethod.GET, "/mbean-metrics/", BodyCodec.string());
        return f.map(HttpResponse::body)
                // uses Gson rather than Vertx's Jackson because Gson is able to handle MBeanMetrics
                // with no additional fuss. Jackson complains about private final fields.
                .map(s -> gson.fromJson(s, MBeanMetrics.class));
    }

    Future<IRecordingDescriptor> startRecording(StartRecordingRequest req) {
        Future<HttpResponse<String>> f =
                invoke(
                        HttpMethod.POST,
                        "/recordings/",
                        Buffer.buffer(gson.toJson(req)),
                        BodyCodec.string());
        return f.map(
                resp -> {
                    int statusCode = resp.statusCode();
                    if (HttpStatusCodeIdentifier.isSuccessCode(statusCode)) {
                        String body = resp.body();
                        return gson.fromJson(body, SerializableRecordingDescriptor.class)
                                .toJmcForm();
                    } else if (statusCode == 403) {
                        throw new AuthorizationErrorException(
                                new UnsupportedOperationException("startRecording"));
                    } else {
                        throw new AgentApiException(statusCode);
                    }
                });
    }

    Future<IRecordingDescriptor> startSnapshot() {
        StartRecordingRequest snapshotReq = new StartRecordingRequest("snapshot", "", "", 0, 0, 0);

        Future<HttpResponse<String>> f =
                invoke(
                        HttpMethod.POST,
                        "/recordings/",
                        Buffer.buffer(gson.toJson(snapshotReq)),
                        BodyCodec.string());

        return f.map(
                resp -> {
                    int statusCode = resp.statusCode();
                    if (HttpStatusCodeIdentifier.isSuccessCode(statusCode)) {
                        String body = resp.body();
                        return gson.fromJson(body, SerializableRecordingDescriptor.class)
                                .toJmcForm();
                    } else if (statusCode == 403) {
                        throw new AuthorizationErrorException(
                                new UnsupportedOperationException("startSnapshot"));
                    } else {
                        throw new AgentApiException(statusCode);
                    }
                });
    }

    Future<Void> updateRecordingOptions(long id, IConstrainedMap<String> newSettings) {
        JsonObject jsonSettings = new JsonObject();
        for (String key : newSettings.keySet()) {
            Object value = newSettings.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof String && StringUtils.isBlank((String) value)) {
                continue;
            }
            jsonSettings.put(key, value);
        }
        Future<HttpResponse<Void>> f =
                invoke(
                        HttpMethod.PATCH,
                        String.format("/recordings/%d", id),
                        jsonSettings.toBuffer(),
                        BodyCodec.none());

        return f.map(
                resp -> {
                    int statusCode = resp.statusCode();
                    if (HttpStatusCodeIdentifier.isSuccessCode(statusCode)) {
                        return null;
                    } else if (statusCode == 403) {
                        throw new AuthorizationErrorException(
                                new UnsupportedOperationException("updateRecordingOptions"));
                    } else {
                        throw new AgentApiException(statusCode);
                    }
                });
    }

    Future<Buffer> openStream(long id) {
        Future<HttpResponse<Buffer>> f =
                invoke(HttpMethod.GET, "/recordings/" + id, BodyCodec.buffer());
        return f.map(
                resp -> {
                    int statusCode = resp.statusCode();
                    if (HttpStatusCodeIdentifier.isSuccessCode(statusCode)) {
                        return resp.body();
                    } else if (statusCode == 403) {
                        throw new AuthorizationErrorException(
                                new UnsupportedOperationException("openStream"));
                    } else {
                        throw new AgentApiException(statusCode);
                    }
                });
    }

    Future<Void> stopRecording(long id) {
        // FIXME this is a terrible hack, the interfaces here should not require only an
        // IConstrainedMap with IOptionDescriptors but allow us to pass other and more simply
        // serializable data to the Agent, such as this recording state entry
        IConstrainedMap<String> map =
                new IConstrainedMap<String>() {
                    @Override
                    public Set<String> keySet() {
                        return Set.of("state");
                    }

                    @Override
                    public Object get(String key) {
                        return RecordingState.STOPPED.name();
                    }

                    @Override
                    public IConstraint<?> getConstraint(String key) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public String getPersistableString(String key) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public IMutableConstrainedMap<String> emptyWithSameConstraints() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public IMutableConstrainedMap<String> mutableCopy() {
                        throw new UnsupportedOperationException();
                    }
                };
        return updateRecordingOptions(id, map);
    }

    Future<Void> deleteRecording(long id) {
        Future<HttpResponse<Void>> f =
                invoke(
                        HttpMethod.DELETE,
                        String.format("/recordings/%d", id),
                        Buffer.buffer(),
                        BodyCodec.none());
        return f.map(
                resp -> {
                    int statusCode = resp.statusCode();
                    if (HttpStatusCodeIdentifier.isSuccessCode(statusCode)) {
                        return null;
                    } else if (statusCode == 403) {
                        throw new AuthorizationErrorException(
                                new UnsupportedOperationException("deleteRecording"));
                    } else {
                        throw new AgentApiException(statusCode);
                    }
                });
    }

    Future<List<IRecordingDescriptor>> activeRecordings() {
        Future<HttpResponse<String>> f = invoke(HttpMethod.GET, "/recordings/", BodyCodec.string());
        return f.map(HttpResponse::body)
                .map(
                        s ->
                                (List<SerializableRecordingDescriptor>)
                                        gson.fromJson(
                                                s,
                                                new TypeToken<
                                                        List<
                                                                SerializableRecordingDescriptor>>() {}.getType()))
                .map(arr -> arr.stream().map(SerializableRecordingDescriptor::toJmcForm).toList());
    }

    Future<Collection<? extends IEventTypeInfo>> eventTypes() {
        Future<HttpResponse<JsonArray>> f =
                invoke(HttpMethod.GET, "/event-types/", BodyCodec.jsonArray());
        return f.map(HttpResponse::body)
                .map(arr -> arr.stream().map(o -> new AgentEventTypeInfo((JsonObject) o)).toList());
    }

    Future<IConstrainedMap<EventOptionID>> eventSettings() {
        Future<HttpResponse<JsonArray>> f =
                invoke(HttpMethod.GET, "/event-settings/", BodyCodec.jsonArray());
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
                invoke(HttpMethod.GET, "/event-templates/", BodyCodec.jsonArray());
        return f.map(HttpResponse::body).map(arr -> arr.stream().map(Object::toString).toList());
    }

    private <T> Future<HttpResponse<T>> invoke(HttpMethod mtd, String path, BodyCodec<T> codec) {
        return invoke(mtd, path, null, codec);
    }

    private <T> Future<HttpResponse<T>> invoke(
            HttpMethod mtd, String path, Buffer payload, BodyCodec<T> codec) {
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
                                        throw new IllegalStateException(e);
                                    }

                                    try {
                                        if (payload != null) {
                                            return req.sendBuffer(payload)
                                                    .toCompletionStage()
                                                    .toCompletableFuture()
                                                    .get();
                                        } else {
                                            return req.send()
                                                    .toCompletionStage()
                                                    .toCompletableFuture()
                                                    .get();
                                        }
                                    } catch (InterruptedException | ExecutionException e) {
                                        logger.error(e);
                                        throw new RuntimeException(e);
                                    }
                                },
                                executor)
                        .exceptionally(
                                t -> {
                                    throw new RuntimeException(t);
                                }));
    }

    static class Factory {

        private final ExecutorService executor;
        private final Gson gson;
        private final long httpTimeout;
        private final WebClient webClient;
        private final CredentialsManager credentialsManager;
        private final Logger logger;

        Factory(
                ExecutorService executor,
                Gson gson,
                long httpTimeout,
                WebClient webClient,
                CredentialsManager credentialsManager,
                Logger logger) {
            this.executor = executor;
            this.gson = gson;
            this.httpTimeout = httpTimeout;
            this.webClient = webClient;
            this.credentialsManager = credentialsManager;
            this.logger = logger;
        }

        AgentClient create(URI agentUri) {
            return new AgentClient(
                    executor, gson, httpTimeout, webClient, credentialsManager, agentUri, logger);
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
