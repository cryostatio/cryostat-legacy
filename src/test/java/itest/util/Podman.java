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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.cryostat.core.sys.Environment;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

public abstract class Podman {

    private Podman() {}

    // this can take some time if an image needs to be pulled
    public static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(60);

    public static final String POD_NAME;

    public static final ExecutorService POOL = Executors.newCachedThreadPool();

    static {
        Environment env = new Environment();
        POD_NAME = env.getProperty("cryostatPodName");
    }

    public static String run(ImageSpec imageSpec) throws Exception {
        List<String> args = new ArrayList<>();
        args.add("run");
        args.add("--quiet");
        args.add("--pod=" + POD_NAME);
        args.add("--detach");
        args.add("--rm");

        imageSpec
                .envs
                .entrySet()
                .forEach(
                        env -> {
                            args.add("--env");
                            args.add(env.getKey() + "=" + env.getValue());
                        });

        args.add(imageSpec.imageSpec);

        return runCommand(args.toArray(new String[0])).out();
    }

    public static Future<Void> waitForContainerState(String id, String state) {
        CompletableFuture<Void> cf = new CompletableFuture<>();

        POOL.submit(
                () -> {
                    try {
                        long start = System.currentTimeMillis();
                        long elapsed = 0;
                        String fmtState = String.format("\"%s\"", Objects.requireNonNull(state));
                        while (elapsed < STARTUP_TIMEOUT.toMillis()) {
                            String out =
                                    runCommand(
                                                    "container",
                                                    "inspect",
                                                    "--format=\"{{.State.Status}}\"",
                                                    id)
                                            .out();
                            if (fmtState.trim().equalsIgnoreCase(out)) {
                                break;
                            }
                            long now = System.currentTimeMillis();
                            long delta = now - start;
                            elapsed += delta;
                            Thread.sleep(2_000L);
                        }
                        if (elapsed >= STARTUP_TIMEOUT.toMillis()) {
                            throw new PodmanException(
                                    String.format(
                                            "Container %s did not reach %s state in %ds",
                                            id, fmtState, STARTUP_TIMEOUT.toSeconds()));
                        }
                        cf.complete(null);
                    } catch (Exception e) {
                        cf.completeExceptionally(e);
                    }
                });

        return cf;
    }

    public static String kill(String id) throws Exception {
        return runCommand("kill", id).out();
    }

    private static CommandOutput runCommand(String... args) throws Exception {
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
                System.out.println(co.err());
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
        public final Map<String, String> envs;
        public final String imageSpec;

        public ImageSpec(String imageSpec) {
            this(imageSpec, Collections.emptyMap());
        }

        public ImageSpec(String imageSpec, Map<String, String> envs) {
            this.imageSpec = imageSpec;
            this.envs = Collections.unmodifiableMap(envs);
        }
    }
}
