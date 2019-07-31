package com.redhat.rhjmc.containerjfr.platform;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.redhat.rhjmc.containerjfr.core.util.log.Logger;

import org.apache.commons.lang3.exception.ExceptionUtils;

class DefaultPlatformClient implements PlatformClient {

    private static final int TESTED_PORT = 9091;
    private static final int THREAD_COUNT = 16;

    @Override
    public List<ServiceRef> listDiscoverableServices() {
        List<ServiceRef> result = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        try {
            byte[] localAddress = InetAddress.getLocalHost().getAddress();
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
            Logger.INSTANCE.debug(ExceptionUtils.getStackTrace(ie));
        } catch (IOException ioe) {
            Logger.INSTANCE.debug(ExceptionUtils.getStackTrace(ioe));
            return Collections.emptyList();
        } finally {
            executor.shutdown();
        }
        return Collections.unmodifiableList(result);
    }

    private ServiceRef testHostByAddress(byte[] addr) {
        try {
            return testHost(InetAddress.getByAddress(addr));
        } catch (IOException ignored) {
            return null;
        }
    }

    private ServiceRef testHost(InetAddress addr) throws IOException {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(addr, TESTED_PORT), 100);
            return new ServiceRef(addr.getCanonicalHostName(), addr.getHostAddress(), TESTED_PORT);
        }
    }
}
