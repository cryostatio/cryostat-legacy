package es.andrewazor.containertest;

import java.util.Arrays;
import java.util.logging.LogManager;

import javax.inject.Singleton;

import org.eclipse.core.runtime.RegistryFactory;

import dagger.BindsInstance;
import dagger.Component;
import es.andrewazor.containertest.jmc.RegistryProvider;
import es.andrewazor.containertest.tui.CommandExecutor;
import es.andrewazor.containertest.tui.CommandExecutor.ExecutionMode;

class JMXClient {
    public static void main(String[] args) throws Exception {
        LogManager.getLogManager().reset();

        System.out.println(String.format("%s started. args: %s",
                System.getProperty("java.rmi.server.hostname", "cjfr-client"), Arrays.asList(args).toString()));
        RegistryFactory.setDefaultRegistryProvider(new RegistryProvider());

        DaggerJMXClient_Client
            .builder()
            .mode(args.length == 0 ? ExecutionMode.INTERACTIVE : ExecutionMode.BATCH)
            .build()
            .commandExecutor()
            .run(args);
    }

    @Singleton
    @Component(modules = { MainModule.class })
    interface Client {
        CommandExecutor commandExecutor();

        @Component.Builder
        interface Builder {
            @BindsInstance Builder mode(ExecutionMode mode);
            Client build();
        }
    }

}
