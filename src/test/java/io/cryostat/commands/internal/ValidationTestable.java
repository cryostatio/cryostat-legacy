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
package io.cryostat.commands.internal;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.cryostat.commands.Command;

interface ValidationTestable {
    static final String TARGET_ID = "TARGET_ID";
    static final String RECORDING_NAME = "RECORDING_NAME";
    static final String RECORDING_DURATION = "RECORDING_DURATION";
    static final String RECORDING_EVENT_SPECIFIER = "RECORDING_EVENT_SPECIFIER";
    static final String SEARCH_TERM = "SEARCH_TERM";

    static final String MOCK_TARGET_ID = "someHost:9091";
    static final String MOCK_RECORDING_NAME = "fooRecording";
    static final String MOCK_RECORDING_DURATION = "30";
    static final String MOCK_RECORDING_EVENT_SPECIFIER = "template=ALL";
    static final String MOCK_SEARCH_TERM = "searchTerm";

    Command commandForValidationTesting();

    List<String> argumentSignature();

    default String[] getArgs(Stream<String> args) {
        return args.map(
                        arg -> {
                            switch (arg) {
                                case TARGET_ID:
                                    return MOCK_TARGET_ID;
                                case RECORDING_NAME:
                                    return MOCK_RECORDING_NAME;
                                case RECORDING_DURATION:
                                    return MOCK_RECORDING_DURATION;
                                case RECORDING_EVENT_SPECIFIER:
                                    return MOCK_RECORDING_EVENT_SPECIFIER;
                                case SEARCH_TERM:
                                    return MOCK_SEARCH_TERM;
                                default:
                                    return arg;
                            }
                        })
                .collect(Collectors.toList())
                .toArray(new String[0]);
    }
}
