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
package io.cryostat;

import java.io.IOException;
import java.io.InputStream;
import java.security.Security;
import java.util.logging.LogManager;

import javax.inject.Singleton;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.CryostatCore;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
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
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

class Cryostat extends AbstractVerticle {

    private final Environment environment = new Environment();
    private final Client client;
    private final Logger logger = Logger.INSTANCE;

    private Cryostat(Client client) {
        this.client = client;
    }

    @Override
    public void start(Promise<Void> future) {
        logger.trace("env: {}", environment.getEnv().toString());

        try {
            client.credentialsManager().migrate();
            client.ruleRegistry().loadRules();
        } catch (Exception e) {
            logger.error(e);
            future.fail(e);
            return;
        }

        logger.info(
                "{} started, version: {}.", instanceName(), client.version().getVersionString());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(null)));

        client.deployer()
                .deploy(client.httpServer(), false)
                .compose(
                        (m) -> {
                            return client.deployer().deploy(client.webServer(), false);
                        })
                .compose(
                        (m) -> {
                            return client.deployer().deploy(client.messagingServer(), false);
                        })
                .compose(
                        (m) -> {
                            return client.deployer().deploy(client.ruleProcessor(), true);
                        })
                .compose(
                        (m) -> {
                            return client.deployer()
                                    .deploy(client.recordingMetadataManager(), true);
                        })
                .compose(
                        (m) -> {
                            return client.deployer().deploy(client.discoveryStorage(), true);
                        })
                .onSuccess(cf -> future.complete())
                .onFailure(
                        t -> {
                            future.fail((Throwable) t);
                            shutdown((Throwable) t);
                        });
    }

    @Override
    public void stop() {
        shutdown(null);
    }

    private String instanceName() {
        return System.getProperty("java.rmi.server.hostname", "cryostat");
    }

    private void shutdown(Throwable cause) {
        if (cause != null) {
            if (!(cause instanceof Exception)) {
                cause = new RuntimeException(cause);
            }
            logger.error((Exception) cause);
        }
        logger.info("{} shutting down...", instanceName());
        client.vertx().close().onComplete(n -> logger.info("Shutdown complete"));
    }

    public static void main(String[] args) throws IOException {
        final Client client = DaggerCryostat_Client.builder().build();
        CryostatCore.initialize();
        try (InputStream config = Cryostat.class.getResourceAsStream("logging.properties")) {
            LogManager.getLogManager()
                    .updateConfiguration(config, k -> ((o, n) -> n != null ? n : o));
        }

        Security.addProvider(BouncyCastleProviderSingleton.getInstance());

        client.vertx().deployVerticle(new Cryostat(client));
    }

    @Singleton
    @Component(modules = {MainModule.class})
    interface Client {
        ApplicationVersion version();

        Vertx vertx();

        VerticleDeployer deployer();

        DiscoveryStorage discoveryStorage();

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
