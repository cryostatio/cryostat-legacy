/*-
 * #%L
 * Container JFR
 * %%
 * Copyright (C) 2020 Red Hat, Inc.
 * %%
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * #L%
 */
package itest.util;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;

import com.redhat.rhjmc.containerjfr.core.sys.Environment;

public abstract class Podman {

    private Podman() {}

    // this can take some time if an image needs to be pulled
    public static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(60);

    public static final String POD_NAME;

    static {
        Environment env = new Environment();
        POD_NAME = env.getProperty("containerJfrPodName");
    }

    public static String run(String imageSpec) throws Exception {
        Process proc = null;
        try {
            String[] args = {
                "podman", "run", "--quiet", "--pod=" + POD_NAME, "--detach", "--rm", imageSpec
            };
            System.out.println(String.join(" ", args));
            proc = new ProcessBuilder().command(args).start();
            proc.waitFor(STARTUP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            String out = IOUtils.toString(proc.getInputStream(), "UTF-8").trim();
            String err = IOUtils.toString(proc.getErrorStream(), "UTF-8").trim();
            System.out.println(out);
            System.out.println(err);
            if (proc.exitValue() != 0) {
                throw new PodmanException(proc.exitValue(), out, err);
            }
            return out;
        } finally {
            if (proc != null) {
                proc.destroyForcibly();
            }
        }
    }

    public static void waitForContainerState(String id, String state) throws Exception {
        long start = System.currentTimeMillis();
        long elapsed = 0;
        state = String.format("\"%s\"", Objects.requireNonNull(state));
        while (elapsed < STARTUP_TIMEOUT.toMillis()) {
            Process proc = null;
            try {
                String[] args = {
                    "podman", "container", "inspect", "--format=\"{{.State.Status}}\"", id
                };
                System.out.println(String.join(" ", args));
                proc = new ProcessBuilder().command(args).start();
                proc.waitFor(STARTUP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                String out = IOUtils.toString(proc.getInputStream(), "UTF-8").trim();
                String err = IOUtils.toString(proc.getErrorStream(), "UTF-8").trim();
                if (proc.exitValue() != 0) {
                    throw new PodmanException(proc.exitValue(), out, err);
                }
                if (state.trim().equalsIgnoreCase(out)) {
                    break;
                }
                System.out.println(out);
                long now = System.currentTimeMillis();
                long delta = now - start;
                elapsed += delta;
                Thread.sleep(5_000L);
            } finally {
                if (proc != null) {
                    proc.destroyForcibly();
                }
            }
        }
        if (elapsed >= STARTUP_TIMEOUT.toMillis()) {
            throw new PodmanException(
                    String.format(
                            "Container %s did not reach %s state in %ds",
                            id, state, STARTUP_TIMEOUT.toSeconds()));
        }
    }

    public static String rm(String id) throws Exception {
        Process proc = null;
        try {
            String[] args = {"podman", "rm", "--force", id};
            System.out.println(String.join(" ", args));
            proc = new ProcessBuilder().command(args).start();
            proc.waitFor(STARTUP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            String out = IOUtils.toString(proc.getInputStream(), "UTF-8").trim();
            String err = IOUtils.toString(proc.getErrorStream(), "UTF-8").trim();
            if (proc.exitValue() != 0) {
                throw new PodmanException(proc.exitValue(), out, err);
            }
            return out;
        } finally {
            if (proc != null) {
                proc.destroyForcibly();
            }
        }
    }

    public static class PodmanException extends IOException {
        PodmanException(int status, String out, String err) {
            super(String.format("Exit status %d: out: %s - err: %s", status, out, err));
        }

        PodmanException(String reason) {
            super(reason);
        }
    }
}
