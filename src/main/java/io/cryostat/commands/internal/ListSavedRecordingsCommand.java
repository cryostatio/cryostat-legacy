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

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.ArrayUtils;

import io.cryostat.MainModule;
import io.cryostat.commands.SerializableCommand;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.core.tui.ClientWriter;
import io.cryostat.jmc.serialization.SavedRecordingDescriptor;
import io.cryostat.net.web.WebServer;

/** @deprecated Use HTTP GET /api/v1/recordings */
@Deprecated
@Singleton
class ListSavedRecordingsCommand implements SerializableCommand {

    private final ClientWriter cw;
    private final FileSystem fs;
    private final Path recordingsPath;
    private final WebServer exporter;

    @Inject
    ListSavedRecordingsCommand(
            ClientWriter cw,
            FileSystem fs,
            @Named(MainModule.RECORDINGS_PATH) Path recordingsPath,
            WebServer exporter) {
        this.cw = cw;
        this.fs = fs;
        this.recordingsPath = recordingsPath;
        this.exporter = exporter;
    }

    @Override
    public String getName() {
        return "list-saved";
    }

    @Override
    public void execute(String[] args) throws Exception {
        cw.println("Saved recordings:");
        List<String> saved = fs.listDirectoryChildren(recordingsPath);
        if (saved.isEmpty()) {
            cw.println("\tNone");
        }
        for (String file : saved) {
            SavedRecordingDescriptor descriptor =
                    new SavedRecordingDescriptor(
                            file,
                            exporter.getArchivedDownloadURL(file),
                            exporter.getArchivedReportURL(file));
            cw.println(toString(descriptor));
        }
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        List<SavedRecordingDescriptor> recordings = new ArrayList<>();
        try {
            for (String name : fs.listDirectoryChildren(recordingsPath)) {
                recordings.add(
                        new SavedRecordingDescriptor(
                                name,
                                exporter.getArchivedDownloadURL(name),
                                exporter.getArchivedReportURL(name)));
            }
        } catch (IOException | URISyntaxException e) {
            return new ExceptionOutput(e);
        }
        return new ListOutput<>(recordings);
    }

    @Override
    public void validate(String[] args) throws FailedValidationException {
        if (args.length != 0) {
            String errorMessage = "No arguments expected";
            cw.println(errorMessage);
            throw new FailedValidationException(errorMessage);
        }
    }

    @Override
    public boolean isAvailable() {
        return fs.isDirectory(recordingsPath);
    }

    private static String toString(SavedRecordingDescriptor descriptor) throws Exception {
        StringBuilder sb = new StringBuilder();
        Method[] methods = ArrayUtils.addAll(descriptor.getClass().getDeclaredMethods());

        List<String> urls = new ArrayList<String>();
        for (Method m : methods) {
            if (m.getParameterTypes().length == 0
                    && (m.getName().startsWith("get") || m.getName().startsWith("is"))) {
                if (m.getName().toLowerCase().contains("url")) {
                    urls.add(String.format("\t%s\t\t%s%n", m.getName(), m.invoke(descriptor)));
                } else {
                    sb.append(String.format("\t%s\t\t%s%n", m.getName(), m.invoke(descriptor)));
                }
            }
        }

        Collections.sort(urls);
        for (String s : urls) {
            sb.append(s);
        }

        return sb.toString();
    }
}
