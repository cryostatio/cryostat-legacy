/*-
 * #%L
 * Cryostat
 * %%
 * Copyright (C) 2020 - 2021 Cryostat
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

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.openjdk.jmc.common.unit.IOptionDescriptor;

import io.cryostat.core.tui.ClientWriter;
import io.cryostat.jmc.serialization.SerializableOptionDescriptor;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;

@Singleton
class ListRecordingOptionsCommand extends AbstractConnectedCommand {

    private final ClientWriter cw;

    @Inject
    ListRecordingOptionsCommand(ClientWriter cw, TargetConnectionManager targetConnectionManager) {
        super(targetConnectionManager);
        this.cw = cw;
    }

    @Override
    public String getName() {
        return "list-recording-options";
    }

    @Override
    public Output<?> execute(String[] args) {
        try {
            String targetId = args[0];
            return targetConnectionManager.executeConnectedTask(
                    new ConnectionDescriptor(targetId),
                    connection -> {
                        Map<String, IOptionDescriptor<?>> origOptions =
                                connection.getService().getAvailableRecordingOptions();
                        Map<String, SerializableOptionDescriptor> options =
                                new HashMap<>(origOptions.size());
                        for (Map.Entry<String, IOptionDescriptor<?>> entry :
                                origOptions.entrySet()) {
                            options.put(
                                    entry.getKey(),
                                    new SerializableOptionDescriptor(entry.getValue()));
                        }
                        return new MapOutput<>(options);
                    });
        } catch (Exception e) {
            return new ExceptionOutput(e);
        }
    }

    @Override
    public void validate(String[] args) throws FailedValidationException {
        if (args.length != 1) {
            String errorMessage =
                    "Expected one argument: hostname:port, ip:port, or JMX service URL";
            cw.println(errorMessage);
            throw new FailedValidationException(errorMessage);
        }

        if (!validateNoNullArgs(args)) {
            String errorMessage = "One or more arguments were null";
            cw.println(errorMessage);
            throw new FailedValidationException(errorMessage);
        }

        if (!validateTargetId(args[0])) {
            String errorMessage = String.format("%s is an invalid connection specifier", args[0]);
            cw.println(errorMessage);
            throw new FailedValidationException(errorMessage);
        }
    }
}
