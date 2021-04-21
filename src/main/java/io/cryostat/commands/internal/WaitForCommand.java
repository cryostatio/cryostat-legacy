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

import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.core.sys.Clock;
import io.cryostat.core.tui.ClientWriter;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;

@Singleton
class WaitForCommand extends AbstractConnectedCommand {

    protected final ClientWriter cw;
    protected final Clock clock;

    @Inject
    WaitForCommand(ClientWriter cw, TargetConnectionManager targetConnectionManager, Clock clock) {
        super(targetConnectionManager);
        this.cw = cw;
        this.clock = clock;
    }

    @Override
    public String getName() {
        return "wait-for";
    }

    /**
     * One arg expected. Given a recording name, this will slowly spinlock on recording completion.
     */
    @Override
    public void execute(String[] args) throws Exception {
        String targetId = args[0];
        String recordingName = args[1];
        targetConnectionManager.executeConnectedTask(
                new ConnectionDescriptor(targetId),
                connection -> {
                    Optional<IRecordingDescriptor> d = getDescriptorByName(targetId, recordingName);
                    if (!d.isPresent()) {
                        cw.println(
                                String.format(
                                        "Recording with name \"%s\" not found in target JVM",
                                        recordingName));
                        return null;
                    }
                    IRecordingDescriptor descriptor = d.get();

                    if (descriptor.isContinuous()
                            && !descriptor
                                    .getState()
                                    .equals(IRecordingDescriptor.RecordingState.STOPPED)) {
                        cw.println(
                                String.format(
                                        "Recording \"%s\" is continuous, refusing to wait",
                                        recordingName));
                        return null;
                    }
                    long recordingStart = descriptor.getDataStartTime().longValue();
                    long recordingEnd = descriptor.getDataEndTime().longValue();
                    long recordingLength = recordingEnd - recordingStart;
                    int lastDots = 0;
                    boolean progressFlag = false;
                    while (!descriptor
                            .getState()
                            .equals(IRecordingDescriptor.RecordingState.STOPPED)) {
                        long recordingElapsed =
                                connection.getApproximateServerTime(clock) - recordingStart;
                        double elapsedProportion =
                                ((double) recordingElapsed) / ((double) recordingLength);
                        int currentDots = (int) Math.ceil(10 * elapsedProportion);
                        if (currentDots > lastDots) {
                            for (int i = 0; i < 2 * currentDots; i++) {
                                cw.print('\b');
                            }
                            cw.print(". ".repeat(currentDots).trim());
                            lastDots = currentDots;
                        } else {
                            progressFlag = !progressFlag;
                            if (progressFlag) {
                                cw.print('\b');
                            } else {
                                cw.print('.');
                            }
                        }
                        clock.sleep(TimeUnit.SECONDS, 1);
                        descriptor = getDescriptorByName(targetId, recordingName).get();
                    }
                    cw.println();
                    return null;
                });
    }

    @Override
    public void validate(String[] args) throws FailedValidationException {
        if (args.length != 2) {
            String errorMessage =
                    "Expected two arguments: target (host:port, ip:port, or JMX service URL) and recording name";
            cw.println(errorMessage);
            throw new FailedValidationException(errorMessage);
        }

        if (!validateNoNullArgs(args)) {
            String errorMessage = "One or more arguments were null";
            cw.println(errorMessage);
            throw new FailedValidationException(errorMessage);
        }

        String targetID = args[0];
        String recordingName = args[1];
        StringJoiner combinedErrorMessage = new StringJoiner("; ");

        if (!validateTargetId(targetID)) {
            String errorMessage = String.format("%s is an invalid connection specifier", targetID);
            cw.println(errorMessage);
            combinedErrorMessage.add(errorMessage);
        }

        if (!validateRecordingName(recordingName)) {
            String errorMessage = String.format("%s is an invalid recording name", recordingName);
            cw.println(errorMessage);
            combinedErrorMessage.add(errorMessage);
        }

        if (combinedErrorMessage.length() > 0) {
            throw new FailedValidationException(combinedErrorMessage.toString());
        }
    }
}
