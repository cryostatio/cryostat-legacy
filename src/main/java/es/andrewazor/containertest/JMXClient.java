package es.andrewazor.containertest;

import java.util.Arrays;
import java.util.logging.LogManager;

import javax.inject.Singleton;

import org.eclipse.core.runtime.RegistryFactory;

import dagger.Component;
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
                Client client = DaggerJMXClient_Client.builder().build();
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
    }

}
