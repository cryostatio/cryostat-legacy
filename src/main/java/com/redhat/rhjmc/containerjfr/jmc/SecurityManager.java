package com.redhat.rhjmc.containerjfr.jmc;

import java.util.Collections;
import java.util.Set;

import com.redhat.rhjmc.containerjfr.jmc.internal.Store;
import org.apache.commons.lang3.NotImplementedException;

import org.openjdk.jmc.ui.common.security.ActionNotGrantedException;
import org.openjdk.jmc.ui.common.security.FailedToSaveException;
import org.openjdk.jmc.ui.common.security.ISecurityManager;
import org.openjdk.jmc.ui.common.security.SecurityException;

public class SecurityManager implements ISecurityManager {

    private final Store store;

    public SecurityManager() {
        this.store = new Store();
    }

    @Override
    public boolean hasKey(String key) {
        return this.store.hasKey(key);
    }

    @Override
    public Object withdraw(String key) throws SecurityException {
        return this.store.remove(key);
    }

    @Override
    public void clearFamily(String family, Set<String> keys) throws FailedToSaveException {
        this.store.clearFamily(family, keys);
    }

    @Override
    public Object get(String key) throws SecurityException {
        return hasKey(key) ? this.store.get(key) : null;
    }

    @Override
    public String store(byte ... value) throws SecurityException {
        return this.store.insert(null, true, value);
    }

    @Override
    public String store(String ... value) throws SecurityException {
        return this.store.insert(null, true, value);
    }

    @Override
    public String storeInFamily(String family, byte ... value) throws SecurityException {
        return this.store.insert(family, true, value);
    }

    @Override
    public String storeInFamily(String family, String ... value) throws SecurityException {
        return this.store.insert(family, true, value);
    }

    @Override
    public void storeWithKey(String key, byte ... value) throws SecurityException {
        this.store.insert(key, false, value);
    }

    @Override
    public void storeWithKey(String key, String ... value) throws SecurityException {
        this.store.insert(key, false, value);
    }

    @Override
    public Set<String> getEncryptionCiphers() {
        return Collections.emptySet();
    }

    @Override
    public String getEncryptionCipher() {
        return null;
    }

    @Override
    public void setEncryptionCipher(String encryptionCipher) throws SecurityException {
        throw new NotImplementedException("Encryption not supported");
    }

    @Override
    public void changeMasterPassword() throws SecurityException {
        throw new NotImplementedException("Master Password change not implemented");
    }

    @Override
    public boolean isLocked() {
        return false;
    }

    @Override
    public void unlock() throws ActionNotGrantedException {
        // no-op
    }

}
