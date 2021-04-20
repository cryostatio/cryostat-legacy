/*-
 * #%L
 * Cryostat
 * %%
 * Copyright (C) 2020 - 2021 The Cryostat Authors
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
package io.cryostat.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.cryostat.core.log.Logger;

public class JavaProcess {

    static Process exec(
            Class<?> klazz, Map<String, String> env, List<String> jvmArgs, List<String> processArgs)
            throws IOException, InterruptedException {
        String className = klazz.getName();

        var cmd = new ArrayList<String>();
        cmd.add("java");
        cmd.addAll(jvmArgs);
        cmd.add("-cp");
        cmd.add("/app/resources:/app/classes:/app/libs/*");
        cmd.add(className);
        cmd.addAll(processArgs);

        Logger.INSTANCE.trace("Forking process: " + cmd.toString());
        var pb = new ProcessBuilder();
        pb.environment().putAll(env);
        return pb.command(cmd).inheritIO().start();
    }

    public static class Builder {
        private Class<?> klazz;
        private Map<String, String> env;
        private List<String> jvmArgs;
        private List<String> processArgs;

        public Builder klazz(Class<?> klazz) {
            this.klazz = Objects.requireNonNull(klazz);
            return this;
        }

        public Builder env(Map<String, String> env) {
            this.env = env;
            return this;
        }

        public Builder jvmArgs(List<String> jvmArgs) {
            this.jvmArgs = jvmArgs;
            return this;
        }

        public Builder processArgs(List<String> processArgs) {
            this.processArgs = processArgs;
            return this;
        }

        public Process exec() throws IOException, InterruptedException {
            Objects.requireNonNull(klazz, "Class cannot be null");
            if (env == null) {
                env = Collections.emptyMap();
            }
            if (jvmArgs == null) {
                jvmArgs = Collections.emptyList();
            }
            if (processArgs == null) {
                processArgs = Collections.emptyList();
            }
            return JavaProcess.exec(klazz, env, jvmArgs, processArgs);
        }
    }
}
