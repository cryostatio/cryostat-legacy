/*-
 * #%L
 * Cryostat
 * %%
 * Copyright (C) 2020 - 2021 The Cryostat Authors
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
package io.cryostat;

import java.util.Arrays;
import java.util.stream.Collectors;

import javax.inject.Singleton;

import dagger.BindsInstance;
import dagger.Component;

import io.cryostat.core.CryostatCore;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.net.HttpServer;
import io.cryostat.net.web.WebServer;
import io.cryostat.tui.CommandExecutor;
import io.cryostat.tui.ws.MessagingServer;

class Cryostat {

    public static void main(String[] args) throws Exception {
        CryostatCore.initialize();

        final Logger logger = Logger.INSTANCE;
        final Environment environment = new Environment();

        logger.trace(String.format("env: %s", environment.getEnv().toString()));

        logger.info(
                String.format(
                        "%s started. args: %s",
                        System.getProperty("java.rmi.server.hostname", "cryostat"),
                        Arrays.stream(args)
                                .map(s -> "\"" + s + "\"")
                                .collect(Collectors.toList())
                                .toString()));

        Client client = DaggerCryostat_Client.builder().mode(ExecutionMode.WEBSOCKET).build();

        client.httpServer().start();
        client.webServer().start();
        client.messagingServer().start();

        client.commandExecutor().run(null);
    }

    @Singleton
    @Component(modules = {MainModule.class})
    interface Client {
        CommandExecutor commandExecutor();

        HttpServer httpServer();

        WebServer webServer();

        MessagingServer messagingServer();

        @Component.Builder
        interface Builder {
            @BindsInstance
            Builder mode(ExecutionMode mode);

            Client build();
        }
    }
}
