package com.redhat.rhjmc.containerjfr;

import java.util.Arrays;
import java.util.stream.Collectors;

import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.core.ContainerJfrCore;
import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.net.web.WebServer;
import com.redhat.rhjmc.containerjfr.tui.CommandExecutor;

import dagger.BindsInstance;
import dagger.Component;
import org.apache.commons.lang3.StringUtils;

class ContainerJfr {
    public static void main(String[] args) throws Exception {
        System.out.println(
                String.format(
                        "%s started. args: %s",
                        System.getProperty("java.rmi.server.hostname", "cjfr-client"),
                        Arrays.stream(args)
                                .map(s -> "\"" + s + "\"")
                                .collect(Collectors.toList())
                                .toString()));
        ContainerJfrCore.initialize();

        final Environment environment = new Environment();
        if (environment
                .getProperty("com.redhat.rhjmc.containerjfr.debug", "false")
                .equals("true")) {
            System.out.println(String.format("env: %s", environment.getEnv().toString()));
            Logger.INSTANCE.setLevel(Logger.Level.ALL);
        }
        final ExecutionMode mode;
        final String clientArgs;
        final int port;
        if (args.length == 0 || args[0].equals("-w")) {
            mode = ExecutionMode.WEBSOCKET;
            clientArgs = null;
        } else if (args[0].equals("-d")) {
            mode = ExecutionMode.SOCKET;
            clientArgs = null;
        } else if (args[0].equals("-it") || StringUtils.isBlank(args[0])) {
            mode = ExecutionMode.INTERACTIVE;
            clientArgs = null;
        } else {
            mode = ExecutionMode.BATCH;
            clientArgs = args[0];
        }

        Client client = DaggerContainerJfr_Client.builder().mode(mode).build();

        client.webServer().start();

        client.commandExecutor().run(clientArgs);
    }

    @Singleton
    @Component(modules = {MainModule.class})
    interface Client {
        CommandExecutor commandExecutor();

        WebServer webServer();

        @Component.Builder
        interface Builder {
            @BindsInstance
            Builder mode(ExecutionMode mode);

            Client build();
        }
    }
}
