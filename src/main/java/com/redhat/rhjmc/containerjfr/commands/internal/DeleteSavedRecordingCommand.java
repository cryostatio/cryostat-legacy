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

import java.nio.file.Path;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;

@Singleton
class DeleteSavedRecordingCommand implements SerializableCommand {

    private final ClientWriter cw;
    private final FileSystem fs;
    private final Path recordingsPath;

    @Inject
    DeleteSavedRecordingCommand(
            ClientWriter cw, FileSystem fs, @Named("RECORDINGS_PATH") Path recordingsPath) {
        this.cw = cw;
        this.fs = fs;
        this.recordingsPath = recordingsPath;
    }

    @Override
    public String getName() {
        return "delete-saved";
    }

    @Override
    public void execute(String[] args) throws Exception {
        String name = args[0];
        if (fs.deleteIfExists(recordingsPath.resolve(name))) {
            cw.println(String.format("\"%s\" deleted", name));
        } else {
            cw.println(String.format("Could not delete saved recording \"%s\"", name));
        }
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        try {
            String name = args[0];
            if (fs.deleteIfExists(recordingsPath.resolve(name))) {
                return new SuccessOutput();
            } else {
                return new FailureOutput(
                        String.format("Could not delete saved recording \"%s\"", name));
            }
        } catch (Exception e) {
            return new ExceptionOutput(e);
        }
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 1) {
            cw.println("Expected one argument: recording name");
            return false;
        }
        boolean isValidRecordingName = validateRecordingName(args[0]);
        if (!isValidRecordingName) {
            cw.println(String.format("%s is an invalid recording name", args[1]));
        }
        return isValidRecordingName;
    }

    @Override
    public boolean isAvailable() {
        return fs.isDirectory(recordingsPath);
    }
}
