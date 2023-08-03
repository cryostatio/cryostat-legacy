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
package io.cryostat.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.cryostat.core.log.Logger;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

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

        @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Field is never mutated")
        public Builder env(Map<String, String> env) {
            this.env = env;
            return this;
        }

        @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Field is never mutated")
        public Builder jvmArgs(List<String> jvmArgs) {
            this.jvmArgs = jvmArgs;
            return this;
        }

        @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Field is never mutated")
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
