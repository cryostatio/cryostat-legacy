package com.redhat.rhjmc.containerjfr.platform.internal;

import com.redhat.rhjmc.containerjfr.platform.PlatformClient;

import dagger.Lazy;

public interface PlatformDetectionStrategy<T extends PlatformClient>
        extends Lazy<T>, Comparable<PlatformDetectionStrategy<?>> {
    int PRIORITY_DEFAULT = 0;
    int PRIORITY_PLATFORM = 50;

    int getPriority();

    boolean isAvailable();

    @Override
    default int compareTo(PlatformDetectionStrategy<?> o) {
        return Integer.compare(o.getPriority(), getPriority());
    }
}
