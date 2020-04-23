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

import javax.inject.Inject;
import javax.inject.Singleton;

import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.jmc.CopyRecordingDescriptor;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.web.WebServer;

@Singleton
class SnapshotCommand extends AbstractRecordingCommand implements SerializableCommand {

    private final WebServer exporter;

    @Inject
    SnapshotCommand(
            ClientWriter cw,
            WebServer exporter,
            EventOptionsBuilder.Factory eventOptionsBuilderFactory,
            RecordingOptionsBuilderFactory recordingOptionsBuilderFactory) {
        super(cw, eventOptionsBuilderFactory, recordingOptionsBuilderFactory);
        this.exporter = exporter;
    }

    @Override
    public String getName() {
        return "snapshot";
    }

    @Override
    public void execute(String[] args) throws Exception {
        IRecordingDescriptor descriptor = getService().getSnapshotRecording();

        String rename =
                String.format("%s-%d", descriptor.getName().toLowerCase(), descriptor.getId());
        cw.println(String.format("Latest snapshot: \"%s\"", rename));

        RecordingOptionsBuilder recordingOptionsBuilder =
                recordingOptionsBuilderFactory.create(getService());
        recordingOptionsBuilder.name(rename);

        getService().updateRecordingOptions(descriptor, recordingOptionsBuilder.build());
        exporter.addRecording(new RenamedSnapshotDescriptor(rename, descriptor));
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        try {
            IRecordingDescriptor descriptor = getService().getSnapshotRecording();

            String rename =
                    String.format("%s-%d", descriptor.getName().toLowerCase(), descriptor.getId());

            RecordingOptionsBuilder recordingOptionsBuilder =
                    recordingOptionsBuilderFactory.create(getService());
            recordingOptionsBuilder.name(rename);

            getService().updateRecordingOptions(descriptor, recordingOptionsBuilder.build());
            exporter.addRecording(new RenamedSnapshotDescriptor(rename, descriptor));

            return new StringOutput(rename);
        } catch (Exception e) {
            return new ExceptionOutput(e);
        }
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 0) {
            cw.println("No arguments expected");
            return false;
        }
        return true;
    }

    private static class RenamedSnapshotDescriptor extends CopyRecordingDescriptor {
        private final String rename;

        RenamedSnapshotDescriptor(String rename, IRecordingDescriptor original) {
            super(original);
            this.rename = rename;
        }

        @Override
        public String getName() {
            return rename;
        }
    }
}
