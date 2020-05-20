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
package com.redhat.rhjmc.containerjfr.commands.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;

import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.util.CheckedConsumer;

class RecordingOptionsCustomizer
        implements Function<RecordingOptionsBuilder, RecordingOptionsBuilder> {

    private final Map<OptionKey, CustomizerConsumer> customizers;
    private final ClientWriter cw;

    RecordingOptionsCustomizer(ClientWriter cw) {
        this.customizers = new HashMap<>();
        this.cw = cw;
    }

    @Override
    public RecordingOptionsBuilder apply(RecordingOptionsBuilder builder) {
        this.customizers.values().forEach(c -> c.accept(builder));
        return builder;
    }

    void set(OptionKey key, String value) {
        CustomizerConsumer consumer = key.mapper.apply(value);
        consumer.setClientWriter(cw);
        customizers.put(key, consumer);
    }

    void unset(OptionKey key) {
        customizers.remove(key);
    }

    enum OptionKey {
        MAX_AGE(
                "maxAge",
                v ->
                        new CustomizerConsumer() {
                            @Override
                            public void acceptThrows(RecordingOptionsBuilder t) throws Exception {
                                t.maxAge(Long.parseLong(v));
                            }
                        }),
        MAX_SIZE(
                "maxSize",
                v ->
                        new CustomizerConsumer() {
                            @Override
                            public void acceptThrows(RecordingOptionsBuilder t) throws Exception {
                                t.maxSize(Long.parseLong(v));
                            }
                        }),
        TO_DISK(
                "toDisk",
                v ->
                        new CustomizerConsumer() {
                            @Override
                            public void acceptThrows(RecordingOptionsBuilder t) throws Exception {
                                t.toDisk(Boolean.parseBoolean(v));
                            }
                        }),
        ;

        private final String name;
        private final Function<String, CustomizerConsumer> mapper;

        OptionKey(String name, Function<String, CustomizerConsumer> mapper) {
            this.name = name;
            this.mapper = mapper;
        }

        static Optional<OptionKey> fromOptionName(String optionName) {
            OptionKey key = null;
            for (OptionKey k : OptionKey.values()) {
                if (k.name.equals(optionName)) {
                    key = k;
                }
            }
            return Optional.ofNullable(key);
        }
    }

    private abstract static class CustomizerConsumer
            implements CheckedConsumer<RecordingOptionsBuilder> {
        private Optional<ClientWriter> cw = Optional.empty();

        void setClientWriter(ClientWriter cw) {
            this.cw = Optional.of(cw);
        }

        @Override
        public void handleException(Exception e) {
            cw.ifPresent(w -> w.println(e));
        }
    }
}
