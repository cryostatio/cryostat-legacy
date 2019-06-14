package com.redhat.rhjmc.containerjfr.jmc.internal;

import org.openjdk.jmc.rjmx.services.internal.CommercialFeaturesServiceFactory;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IContributor;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.InvalidRegistryObjectException;

public class ServiceFactoryConfig implements IConfigurationElement {
    @Override
    public Object createExecutableExtension(String propertyName) throws CoreException {
        if ("factory".equals(propertyName)) {
            return new CommercialFeaturesServiceFactory();
        }
        return null;
    }

    @Override
    public String getAttribute(String name) throws InvalidRegistryObjectException {
        return null;
    }

    @Override
    public String getAttribute(String attrName, String locale) throws InvalidRegistryObjectException {
        return null;
    }

    @Override
    public String getAttributeAsIs(String name) throws InvalidRegistryObjectException {
        return null;
    }

    @Override
    public String[] getAttributeNames() throws InvalidRegistryObjectException {
        return new String[0];
    }

    @Override
    public IConfigurationElement[] getChildren() throws InvalidRegistryObjectException {
        return new IConfigurationElement[0];
    }

    @Override
    public IConfigurationElement[] getChildren(String name) throws InvalidRegistryObjectException {
        return new IConfigurationElement[0];
    }

    @Override
    public IExtension getDeclaringExtension() throws InvalidRegistryObjectException {
        return null;
    }

    @Override
    public String getName() throws InvalidRegistryObjectException {
        return "service";
    }

    @Override
    public Object getParent() throws InvalidRegistryObjectException {
        return null;
    }

    @Override
    public String getValue() throws InvalidRegistryObjectException {
        return null;
    }

    @Override
    public String getValue(String locale) throws InvalidRegistryObjectException {
        return null;
    }

    @Override
    public String getValueAsIs() throws InvalidRegistryObjectException {
        return null;
    }

    @Override
    public String getNamespace() throws InvalidRegistryObjectException {
        return null;
    }

    @Override
    public String getNamespaceIdentifier() throws InvalidRegistryObjectException {
        return null;
    }

    @Override
    public IContributor getContributor() throws InvalidRegistryObjectException {
        return null;
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
