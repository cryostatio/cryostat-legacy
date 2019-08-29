package com.redhat.rhjmc.containerjfr.platform.internal;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.net.NetworkResolver;
import com.redhat.rhjmc.containerjfr.platform.PlatformClient;
import com.redhat.rhjmc.containerjfr.platform.ServiceRef;

class DefaultPlatformClient implements PlatformClient {

    private static final int TESTED_PORT = 9091;
    private static final int CONNECTION_TIMEOUT_MS = 100;
    private static final int THREAD_COUNT = 16;

    private final NetworkResolver resolver;

    DefaultPlatformClient(NetworkResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public List<ServiceRef> listDiscoverableServices() {
        List<ServiceRef> result = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        try {
            byte[] localAddress = resolver.getRawHostAddress();
            CountDownLatch latch = new CountDownLatch(254);

            for (int i = 1; i <= 254; i++) {
                byte[] remote = new byte[] {
                    localAddress[0],
                    localAddress[1],
                    localAddress[2],
                    (byte) i
                };
                executor.submit(() -> {
                    ServiceRef mapping = testHostByAddress(remote);
                    if (mapping != null) {
                        result.add(mapping);
                    }
                    latch.countDown();
                });
            }

            latch.await();
        } catch (InterruptedException ie) {
            Logger.INSTANCE.debug(ie);
        } catch (IOException ioe) {
            Logger.INSTANCE.debug(ioe);
            return Collections.emptyList();
        } finally {
            executor.shutdown();
        }
        return Collections.unmodifiableList(result);
    }

    private ServiceRef testHostByAddress(byte[] addr) {
        try {
            InetAddress host = resolver.resolveAddress(addr);
            if (resolver.testConnection(host, TESTED_PORT, CONNECTION_TIMEOUT_MS)) {
                return new ServiceRef(host.getHostAddress(), resolver.resolveCanonicalHostName(host), TESTED_PORT);
            }
            return null;
        } catch (IOException ignored) {
            return null;
        }
    }

}
