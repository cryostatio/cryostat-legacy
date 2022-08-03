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
package io.cryostat;

import java.io.IOException;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Singleton;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.CryostatCore;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.discovery.BuiltInDiscovery;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.messaging.MessagingServer;
import io.cryostat.net.HttpServer;
import io.cryostat.net.web.WebServer;
import io.cryostat.recordings.RecordingMetadataManager;
import io.cryostat.rules.RuleProcessor;
import io.cryostat.rules.RuleRegistry;

import com.nimbusds.jose.crypto.bc.BouncyCastleProviderSingleton;
import dagger.Component;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;

class Cryostat extends AbstractVerticle {

    private final Environment environment = new Environment();
    private final Client client;
    private final Logger logger = Logger.INSTANCE;

    private final List<Future> futures = new ArrayList<>();

    private Cryostat(Client client) {
        this.client = client;
    }

    @Override
    public void start(Promise<Void> future) {
        logger.trace("env: {}", environment.getEnv().toString());

        try {
            client.credentialsManager().migrate();
            client.credentialsManager().load();
            client.ruleRegistry().loadRules();
            client.recordingMetadataManager().load();
        } catch (Exception e) {
            logger.error(e);
            future.fail(e);
            return;
        }

        logger.info("{} started.", instanceName());

        deploy(client.discoveryStorage(), true);
        deploy(client.discovery(), true);
        deploy(client.httpServer(), false);
        deploy(client.webServer(), false);
        deploy(client.messagingServer(), false);
        deploy(client.ruleProcessor(), true);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(null)));
        CompositeFuture.join(futures)
                .onSuccess(cf -> future.complete())
                .onFailure(
                        t -> {
                            future.fail(t);
                            shutdown(t);
                        });
    }

    @Override
    public void stop() {
        shutdown(null);
    }

    private String instanceName() {
        return System.getProperty("java.rmi.server.hostname", "cryostat");
    }

    private void deploy(Verticle verticle, boolean worker) {
        String name = verticle.getClass().getName();
        logger.info("Deploying {} Verticle", name);
        Future f =
                client.vertx()
                        .deployVerticle(verticle, new DeploymentOptions().setWorker(worker))
                        .onSuccess(id -> logger.info("Deployed {} Verticle [{}]", name, id))
                        .onFailure(
                                t -> {
                                    logger.error("FAILED to deploy {} Verticle", name);
                                    t.printStackTrace();
                                });
        futures.add(f);
    }

    private void shutdown(Throwable cause) {
        if (cause != null) {
            if (!(cause instanceof Exception)) {
                cause = new RuntimeException(cause);
            }
            logger.error((RuntimeException) cause);
        }
        logger.info("{} shutting down...", instanceName());
        client.vertx().close().onComplete(n -> logger.info("Shutdown complete"));
    }

    public static void main(String[] args) throws IOException {
        final Client client = DaggerCryostat_Client.builder().build();
        CryostatCore.initialize();

        Security.addProvider(BouncyCastleProviderSingleton.getInstance());

        client.vertx().deployVerticle(new Cryostat(client));
    }

    @Singleton
    @Component(modules = {MainModule.class})
    interface Client {
        Vertx vertx();

        DiscoveryStorage discoveryStorage();

        BuiltInDiscovery discovery();

        CredentialsManager credentialsManager();

        RuleRegistry ruleRegistry();

        RuleProcessor ruleProcessor();

        HttpServer httpServer();

        WebServer webServer();

        MessagingServer messagingServer();

        RecordingMetadataManager recordingMetadataManager();

        @Component.Builder
        interface Builder {
            Client build();
        }
    }
}
