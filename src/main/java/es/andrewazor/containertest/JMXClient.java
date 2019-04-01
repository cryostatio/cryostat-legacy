package es.andrewazor.containertest;

import java.util.Arrays;
import java.util.logging.LogManager;

import javax.inject.Singleton;

import org.eclipse.core.runtime.RegistryFactory;

import dagger.BindsInstance;
import dagger.Component;
import dagger.Component.Builder;
import es.andrewazor.containertest.jmc.RegistryProvider;

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
                    .clientReader(new TtyClientReader())
                    .clientWriter(new TtyClientWriter())
                    .build();
                client.shell().run(args);
            }
        });
        t.run();
        t.join();
    }

    @Singleton
    @Component(modules = { MainModule.class })
    interface Client {
        Shell shell();

        @Component.Builder
        interface Builder {
            @BindsInstance Builder clientReader(ClientReader cr);
            @BindsInstance Builder clientWriter(ClientWriter cw);
            Client build();
        }
    }

}
