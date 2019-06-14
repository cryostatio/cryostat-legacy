package com.redhat.rhjmc.containerjfr.jmc.internal;

import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Store {

    private final Map<String, Object> store = new HashMap<String, Object>();

    private static final String SEP = "_";

    public String insert(String key, boolean keyFamily, String value) {
        return insertInternal(key, keyFamily, value);
    }

    public String insert(String key, boolean keyFamily, String[] value) {
        return insertInternal(key, keyFamily, value);
    }

    public String insert(String key, boolean keyFamily, byte[] value) {
        return insertInternal(key, keyFamily, value);
    }

    private String generateKey(String family) {
        return (this.store.size() + 1) + SEP + "store" + (family == null ? "" : SEP + family);
    }

    private synchronized String insertInternal(String key, boolean keyFamily, Object value) {
        key = keyFamily || key == null ? generateKey(key) : key;
        this.store.put(key, value);
        return key;
    }

    public synchronized Object get(String key) {
        return this.store.get(key);
    }

    public synchronized void clearFamily(String family, Set<String> keepKeys) {
        Iterator<String> it = this.store.keySet().iterator();
        while (it.hasNext()) {
            String key = it.next();
            String[] keyParts = key.split(SEP);
            if (keyParts.length == 3 && keyParts[2].equals(family) && !keepKeys.contains(key)) {
                it.remove();
            }
        }
    }

    public synchronized boolean hasKey(String key) {
        return this.store.containsKey(key);
    }

    public synchronized Object remove(String key) {
        return this.store.remove(key);
    }

}
