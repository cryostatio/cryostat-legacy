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
import es.andrewazor.containertest.tui.CommandExecutor.Mode;

class JMXClient {
    public static void main(String[] args) throws Exception {
        LogManager.getLogManager().reset();

        System.out.println(String.format("%s started. args: %s",
                System.getProperty("java.rmi.server.hostname", "cjfr-client"), Arrays.asList(args).toString()));
        RegistryFactory.setDefaultRegistryProvider(new RegistryProvider());

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                Client client = DaggerJMXClient_Client
                    .builder()
                    .mode(args.length == 0 ? Mode.INTERACTIVE : Mode.BATCH)
                    .build();
                client.commandExecutor().run(args);
            }
        });
        t.run();
        t.join();
    }

    @Singleton
    @Component(modules = { MainModule.class })
    interface Client {
        CommandExecutor commandExecutor();

        @Component.Builder
        interface Builder {
            @BindsInstance Builder mode(@ExecutionMode Mode mode);
            Client build();
        }
    }

}
