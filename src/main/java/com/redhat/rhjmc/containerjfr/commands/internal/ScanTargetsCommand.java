package com.redhat.rhjmc.containerjfr.commands.internal;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;

@Singleton
class ScanTargetsCommand implements SerializableCommand {

    private static final String KUBERNETES_ENV_SUFFIX = "_PORT_9091_TCP_ADDR";

    private final ClientWriter cw;
    private final Environment env;

    @Inject
    ScanTargetsCommand(ClientWriter cw, Environment env) {
        this.cw = cw;
        this.env = env;
    }

    @Override
    public String getName() {
        return "scan-targets";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 0) {
            cw.println("No arguments expected");
            return false;
        }
        return true;
    }

    @Override
    public void execute(String[] args) throws Exception {
        findCompatibleJvms().forEach(m -> cw.println(String.format("%s -> %s", m.hostname, m.ip)));
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        try {
            return new ListOutput<>(findCompatibleJvms());
        } catch (Exception e) {
            return new ExceptionOutput(e);
        }
    }

    private List<IpHostMapping> findCompatibleJvms() throws UnknownHostException, InterruptedException {
        // Check for environment variables added by Kubernetes that indicate
        // IP addresses listening on port 9091.
        List<IpHostMapping> mapping = env.getEnv().entrySet().parallelStream()
                .filter(e -> e.getKey().endsWith(KUBERNETES_ENV_SUFFIX))
                .map(e -> testHostByName(e.getValue()))
                .filter(m -> m != null)
                .collect(Collectors.toList());
        if (mapping.isEmpty()) {
            // No matches from environment variables, use port scan
            mapping = scan();
        }
        return mapping;
    }

    private IpHostMapping testHostByName(String host) {
        try {
            return testHost(InetAddress.getByName(host));
        } catch (IOException ignored) {
            return null;
        }
    }

    private IpHostMapping testHostByAddress(byte[] addr) {
        try {
            return testHost(InetAddress.getByAddress(addr));
        } catch (IOException ignored) {
            return null;
        }
    }

    private IpHostMapping testHost(InetAddress addr) throws IOException {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(addr, 9091), 100);
            return new IpHostMapping(addr.getHostAddress(), addr.getCanonicalHostName());
        }
    }

    private List<IpHostMapping> scan() throws UnknownHostException, InterruptedException {
        List<IpHostMapping> result = new ArrayList<>();
        int threads = 16;
        CountDownLatch latch = new CountDownLatch(254);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        byte[] localAddress = InetAddress.getLocalHost().getAddress();
        for (int i = 1; i <= 254; i++) {
            byte[] remote = new byte[] {
                localAddress[0],
                localAddress[1],
                localAddress[2],
                (byte) i
            };
            executor.submit(() -> {
                IpHostMapping mapping = testHostByAddress(remote);
                if (mapping != null) {
                    result.add(mapping);
                }
                latch.countDown();
            });
        }

        latch.await();
        executor.shutdown();
        return result;
    }

    static class IpHostMapping {
        final String ip;
        final String hostname;
        IpHostMapping(String ip, String hostname) {
            this.ip = ip;
            this.hostname = hostname;
        }
    }

}
