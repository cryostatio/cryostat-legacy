package com.redhat.rhjmc.containerjfr.jmc.internal;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IContributor;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.InvalidRegistryObjectException;

public class StubExtensionPoint implements IExtensionPoint {
    private static int ID = 0;

    @Override
    public IConfigurationElement[] getConfigurationElements() throws InvalidRegistryObjectException {
        return new IConfigurationElement[0];
    }

    @Override
    public String getNamespace() throws InvalidRegistryObjectException {
        return "stub";
    }

    @Override
    public String getNamespaceIdentifier() throws InvalidRegistryObjectException {
        return "stubId";
    }

    @Override
    public IContributor getContributor() throws InvalidRegistryObjectException {
        return new IContributor() {
            @Override
            public String getName() {
                return "none";
            }
        };
    }

    @Override
    public IExtension getExtension(String extensionId) throws InvalidRegistryObjectException {
        return null;
    }

    @Override
    public IExtension[] getExtensions() throws InvalidRegistryObjectException {
        return new IExtension[0];
    }

    @Override
    public String getLabel() throws InvalidRegistryObjectException {
        return "label";
    }

    @Override
    public String getLabel(String locale) throws InvalidRegistryObjectException {
        return "label";
    }

    @Override
    public String getSchemaReference() throws InvalidRegistryObjectException {
        return null;
    }

    @Override
    public String getSimpleIdentifier() throws InvalidRegistryObjectException {
        return "StubExtensionPoint";
    }

    @Override
    public synchronized String getUniqueIdentifier() throws InvalidRegistryObjectException {
        return "stubId" + StubExtensionPoint.ID++;
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public boolean isValid() {
        return true;
    }
}
