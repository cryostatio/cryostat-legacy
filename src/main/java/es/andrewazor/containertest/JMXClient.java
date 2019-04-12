package es.andrewazor.containertest;

import java.util.Arrays;
import java.util.logging.LogManager;
import java.util.stream.Collectors;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.RegistryFactory;

import dagger.BindsInstance;
import dagger.Component;
import es.andrewazor.containertest.jmc.RegistryProvider;
import es.andrewazor.containertest.sys.Environment;
import es.andrewazor.containertest.tui.CommandExecutor;
import es.andrewazor.containertest.tui.CommandExecutor.ExecutionMode;

class JMXClient {
    public static void main(String[] args) throws Exception {
        LogManager.getLogManager().reset();

        System.out.println(String.format("%s started. args: %s",
                System.getProperty("java.rmi.server.hostname", "cjfr-client"),
                Arrays.asList(args).stream().map(s -> "\"" + s + "\"").collect(Collectors.toList()).toString()));
        RegistryFactory.setDefaultRegistryProvider(new RegistryProvider());

        final ExecutionMode mode;
        final String clientArgs;
        final int port;
        if (args.length == 0 || args[0].equals("-it") || StringUtils.isBlank(args[0])) {
            mode = ExecutionMode.INTERACTIVE;
            clientArgs = null;
            port = -1;
        } else if (args[0].equals("-d")) {
            mode = ExecutionMode.SOCKET;
            clientArgs = null;
            port = Integer.parseInt(new Environment().getEnv("LISTEN_PORT", "9090"));
        } else {
            mode = ExecutionMode.BATCH;
            clientArgs = args[0];
            port = -1;
        }

        DaggerJMXClient_Client
            .builder()
            .mode(mode)
            .port(port)
            .build()
            .commandExecutor()
            .run(clientArgs);
    }

    @Singleton
    @Component(modules = { MainModule.class })
    interface Client {
        CommandExecutor commandExecutor();

        @Component.Builder
        interface Builder {
            @BindsInstance Builder mode(ExecutionMode mode);
            @BindsInstance Builder port(@Named("LISTEN_PORT") int port);
            Client build();
        }
    }

}
