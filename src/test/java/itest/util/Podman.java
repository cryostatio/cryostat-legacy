/*
 * Copyright The Cryostat Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package itest.util;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.cryostat.core.sys.Environment;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

public abstract class Podman {

    private Podman() {}

    // this can take some time if an image needs to be pulled
    public static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(60);

    public static final String POD_NAME;
    public static final int CRYOSTAT_WEB_PORT;

    public static final ExecutorService POOL = Executors.newCachedThreadPool();

    static {
        Environment env = new Environment();
        POD_NAME = env.getProperty("cryostatPodName");
        CRYOSTAT_WEB_PORT = Integer.parseInt(env.getProperty("cryostat.itest.webPort", "8181"));
    }

    public static String runAppWithAgent(int agentHttpPort, ImageSpec spec, boolean preferJmx)
            throws Exception {
        Map<String, String> augmentedEnvs = new HashMap<>(spec.envs);

        augmentedEnvs.putIfAbsent("CRYOSTAT_AGENT_APP_NAME", spec.name);
        augmentedEnvs.putIfAbsent("CRYOSTAT_AGENT_WEBCLIENT_SSL_TRUST_ALL", "true");
        augmentedEnvs.putIfAbsent("CRYOSTAT_AGENT_WEBCLIENT_SSL_VERIFY_HOSTNAME", "false");
        augmentedEnvs.putIfAbsent("CRYOSTAT_AGENT_WEBSERVER_HOST", "localhost");
        augmentedEnvs.putIfAbsent("CRYOSTAT_AGENT_WEBSERVER_PORT", String.valueOf(agentHttpPort));
        augmentedEnvs.putIfAbsent(
                "CRYOSTAT_AGENT_CALLBACK", String.format("http://localhost:%d/", agentHttpPort));
        augmentedEnvs.putIfAbsent(
                "CRYOSTAT_AGENT_BASEURI", String.format("http://localhost:%d/", CRYOSTAT_WEB_PORT));
        augmentedEnvs.putIfAbsent("CRYOSTAT_AGENT_TRUST_ALL", "true");
        augmentedEnvs.putIfAbsent(
                "CRYOSTAT_AGENT_REGISTRATION_PREFER_JMX", String.valueOf(preferJmx));

        ImageSpec augmentedSpec = new ImageSpec(spec.imageSpec, augmentedEnvs);
        List<String> args = new ArrayList<>();
        args.add("run");
        args.add("--name=" + augmentedSpec.name);
        args.add("--quiet");
        args.add("--pod=" + POD_NAME);
        args.add("--health-cmd");
        args.add(
                String.format(
                        "curl --fail http://localhost:%d",
                        Integer.parseInt(spec.envs.getOrDefault("HTTP_PORT", "8081"))));
        args.add("--detach");
        args.add("--rm");

        augmentedSpec.envs.entrySet().stream()
                .map(e -> String.format("%s=%s", e.getKey(), e.getValue()))
                .forEach(
                        env -> {
                            args.add("--env");
                            args.add(env);
                        });
        args.add(augmentedSpec.imageSpec);

        return runCommand(args.toArray(new String[0])).out().strip();
    }

    public static String runAppWithAgent(int agentHttpPort, ImageSpec spec) throws Exception {
        return runAppWithAgent(agentHttpPort, spec, true);
    }

    public static String stop(String id) throws Exception {
        return runCommand("stop", id).out();
    }

    public static CommandOutput runCommand(String... args) throws Exception {
        Process proc = null;
        try {
            List<String> argsList = new ArrayList<>();
            argsList.add("podman");
            argsList.addAll(Arrays.asList(args));
            System.out.println(argsList);
            proc = new ProcessBuilder().command(argsList).start();
            proc.waitFor(STARTUP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            CommandOutput co = new CommandOutput(proc);
            if (StringUtils.isNotBlank(co.out())) {
                System.out.println(co.out());
            }
            if (StringUtils.isNotBlank(co.err())) {
                System.err.println(co.err());
            }
            if (co.exitValue() != 0) {
                throw new PodmanException(co);
            }
            return co;
        } finally {
            if (proc != null) {
                proc.destroyForcibly();
            }
        }
    }

    public static class CommandOutput {

        private final int exitValue;
        private final String out;
        private final String err;

        CommandOutput(Process proc) throws IOException {
            this.exitValue = proc.exitValue();
            this.out = IOUtils.toString(proc.getInputStream(), "UTF-8").trim();
            this.err = IOUtils.toString(proc.getErrorStream(), "UTF-8").trim();
        }

        public int exitValue() {
            return exitValue;
        }

        public String out() {
            return out;
        }

        public String err() {
            return err;
        }
    }

    public static class PodmanException extends IOException {
        PodmanException(CommandOutput co) {
            super(
                    String.format(
                            "Exit status %d: out: %s - err: %s",
                            co.exitValue(), co.out(), co.err()));
        }

        PodmanException(String reason) {
            super(reason);
        }
    }

    public static class ImageSpec {
        public final String name;
        public final String imageSpec;
        public final Map<String, String> envs;

        public ImageSpec(String imageSpec) {
            this(UUID.randomUUID().toString(), imageSpec, Collections.emptyMap());
        }

        public ImageSpec(String imageSpec, Map<String, String> envs) {
            this(UUID.randomUUID().toString(), imageSpec, envs);
        }

        public ImageSpec(String name, String imageSpec, Map<String, String> envs) {
            this.name = Objects.requireNonNull(name);
            this.imageSpec = Objects.requireNonNull(imageSpec);
            this.envs = Collections.unmodifiableMap(envs);
        }
    }
}
