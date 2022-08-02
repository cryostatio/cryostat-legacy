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
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

class Cryostat {

    public static void main(String[] args) {
        final Logger logger = Logger.INSTANCE;
        final Client client = DaggerCryostat_Client.builder().build();
        try {
            CryostatCore.initialize();

            Security.addProvider(BouncyCastleProviderSingleton.getInstance());

            final Environment environment = new Environment();

            logger.trace("env: {}", environment.getEnv().toString());

            logger.info("{} started.", System.getProperty("java.rmi.server.hostname", "cryostat"));

            List<Future> futures = new ArrayList<>();

            client.credentialsManager().migrate();
            client.credentialsManager().load();
            client.ruleRegistry().loadRules();
            client.recordingMetadataManager().load();
            futures.add(
                    client.vertx()
                            .deployVerticle(
                                    client.discoveryStorage(),
                                    new DeploymentOptions().setWorker(true)));
            futures.add(
                    client.vertx()
                            .deployVerticle(
                                    client.discovery(), new DeploymentOptions().setWorker(true)));
            futures.add(
                    client.vertx().deployVerticle(client.httpServer(), new DeploymentOptions()));
            futures.add(
                    client.vertx()
                            .deployVerticle(
                                    client.webServer(), new DeploymentOptions().setWorker(true)));
            futures.add(
                    client.vertx()
                            .deployVerticle(client.messagingServer(), new DeploymentOptions()));
            futures.add(
                    client.vertx()
                            .deployVerticle(
                                    client.ruleProcessor(),
                                    new DeploymentOptions().setWorker(true)));

            Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(null, client, logger)));
            CompositeFuture.join(futures).onFailure(t -> shutdown(t, client, logger));
        } catch (Exception e) {
            shutdown(e, client, logger);
        }
    }

    private static void shutdown(Throwable cause, Client client, Logger logger) {
        if (cause != null) {
            if (!(cause instanceof Exception)) {
                cause = new RuntimeException(cause);
            }
            logger.error((RuntimeException) cause);
        }
        logger.info("Cryostat shutting down...");
        client.vertx().close().onComplete(n -> logger.info("Shutdown complete"));
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
