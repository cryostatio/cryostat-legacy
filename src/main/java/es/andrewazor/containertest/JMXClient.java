package es.andrewazor.containertest;

import java.util.Arrays;
import java.util.logging.LogManager;

import org.eclipse.core.runtime.RegistryFactory;

import es.andrewazor.containertest.jmc.RegistryProvider;

class JMXClient {
    public static void main(String[] args) throws Exception {
        LogManager.getLogManager().reset();

        System.out.println(String.format("JMXClient started. args: %s", Arrays.asList(args).toString()));
        RegistryFactory.setDefaultRegistryProvider(new RegistryProvider());

        Thread t = new Thread(new Shell());
        t.run();
        t.join();
    }

}
