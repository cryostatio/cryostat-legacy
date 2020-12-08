/*-
 * #%L
 * Container JFR
 * %%
 * Copyright (C) 2020 Red Hat, Inc.
 * %%
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
 * #L%
 */
package com.redhat.rhjmc.containerjfr;

import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.commands.CommandExecutor;
import com.redhat.rhjmc.containerjfr.configuration.CredentialsManager;
import com.redhat.rhjmc.containerjfr.core.ContainerJfrCore;
import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.Credentials;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.messaging.MessagingServer;
import com.redhat.rhjmc.containerjfr.net.HttpServer;
import com.redhat.rhjmc.containerjfr.net.web.WebServer;
import com.redhat.rhjmc.containerjfr.platform.PlatformClient;
import com.redhat.rhjmc.containerjfr.rules.RuleRegistry;
import dagger.Component;

class ContainerJfr {

    public static void main(String[] args) throws Exception {
        ContainerJfrCore.initialize();

        final Logger logger = Logger.INSTANCE;
        final Environment environment = new Environment();

        logger.trace("env: {}", environment.getEnv().toString());

        logger.info("{} started.", System.getProperty("java.rmi.server.hostname", "container-jfr"));

        Client client = DaggerContainerJfr_Client.builder().build();

        client.credentialsManager().load();
        // FIXME remove this, only here for testing
        client.credentialsManager()
                .addCredentials(
                        "es.andrewazor.demo.Main", new Credentials("admin", "adminpass123"));
        client.ruleRegistry().loadRules();
        client.httpServer().start();
        client.webServer().start();
        client.messagingServer().start();
        client.platformClient().start();

        client.commandExecutor().run();
    }

    @Singleton
    @Component(modules = {MainModule.class})
    interface Client {
        CredentialsManager credentialsManager();

        RuleRegistry ruleRegistry();

        HttpServer httpServer();

        WebServer webServer();

        MessagingServer messagingServer();

        PlatformClient platformClient();

        CommandExecutor commandExecutor();

        @Component.Builder
        interface Builder {
            Client build();
        }
    }
}
