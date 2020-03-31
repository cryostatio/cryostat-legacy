package com.redhat.rhjmc.containerjfr.platform.internal;

import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.platform.PlatformClient;

public interface PlatformDetectionStrategy<T extends PlatformClient>
        extends Comparable<PlatformDetectionStrategy<?>> {
    int PRIORITY_DEFAULT = 0;
    int PRIORITY_PLATFORM = 50;

    int getPriority();

    boolean isAvailable();

    T getPlatformClient();

    AuthManager getAuthManager();

    @Override
    default int compareTo(PlatformDetectionStrategy<?> o) {
        return Integer.compare(o.getPriority(), getPriority());
    }
}
