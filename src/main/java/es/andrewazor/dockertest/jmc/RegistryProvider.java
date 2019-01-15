package es.andrewazor.dockertest.jmc;

import org.eclipse.core.internal.registry.ExtensionRegistry;
import org.eclipse.core.runtime.spi.IRegistryProvider;
import org.eclipse.core.runtime.spi.RegistryStrategy;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IExtensionPoint;

import es.andrewazor.dockertest.jmc.internal.ServiceFactoryConfig;
import es.andrewazor.dockertest.jmc.internal.StubExtensionPoint;

public class RegistryProvider implements IRegistryProvider {
    @Override
    public IExtensionRegistry getRegistry() {
        return new ExtensionRegistry(new RegistryStrategy(null, null), "", "") {
            @Override
            public IExtensionPoint getExtensionPoint(String id) {
                return new StubExtensionPoint();
            }

            @Override
            public IConfigurationElement[] getConfigurationElementsFor(String id) {
                if ("org.openjdk.jmc.rjmx.service".equals(id)) {
                    return new IConfigurationElement[] { new ServiceFactoryConfig() };
                }
                return new IConfigurationElement[0];
            }
        };
    }
}
