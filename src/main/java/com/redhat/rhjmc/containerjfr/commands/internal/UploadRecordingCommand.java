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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@Singleton
class UploadRecordingCommand extends AbstractConnectedCommand implements SerializableCommand {

    private final ClientWriter cw;
    private final FileSystem fs;
    private final Path recordingsPath;
    private final Provider<CloseableHttpClient> httpClientProvider;

    @Inject
    UploadRecordingCommand(
            ClientWriter cw,
            FileSystem fs,
            @Named("RECORDINGS_PATH") Path recordingsPath,
            Provider<CloseableHttpClient> httpClientProvider) {
        this.cw = cw;
        this.fs = fs;
        this.recordingsPath = recordingsPath;
        this.httpClientProvider = httpClientProvider;
    }

    @Override
    public String getName() {
        return "upload-recording";
    }

    @Override
    public void execute(String[] args) throws Exception {
        ResponseMessage response = doPost(args[0], args[1]);
        cw.println(String.format("[%s] %s", response.status, response.body));
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        try {
            ResponseMessage response = doPost(args[0], args[1]);
            return new MapOutput<>(
                    Map.of(
                            "status", response.status,
                            "body", response.body));
        } catch (Exception e) {
            return new ExceptionOutput(e);
        }
    }

    // try-with-resources generates a "redundant" nullcheck in bytecode
    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE")
    private ResponseMessage doPost(String recordingName, String uploadUrl) throws Exception {
        Optional<InputStream> recording = getBestRecordingForName(recordingName);
        if (!recording.isPresent()) {
            throw new RecordingNotFoundException(recordingName);
        }

        InputStream stream = recording.get();
        HttpPost post = new HttpPost(uploadUrl);
        post.setEntity(
                MultipartEntityBuilder.create()
                        .addBinaryBody(
                                "file", stream, ContentType.APPLICATION_OCTET_STREAM, recordingName)
                        .build());

        try (CloseableHttpClient httpClient = httpClientProvider.get();
                CloseableHttpResponse response = httpClient.execute(post);
                stream) {
            return new ResponseMessage(
                    response.getStatusLine(), EntityUtils.toString(response.getEntity()));
        }
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 2) {
            cw.println("Expected two arguments: recording name and upload URL");
            return false;
        }
        if (!validateRecordingName(args[0])) {
            cw.println(String.format("%s is an invalid recording name", args[0]));
            return false;
        }
        return true;
    }

    @Override
    public boolean isAvailable() {
        return super.isAvailable() || fs.isDirectory(recordingsPath);
    }

    // returned stream should be cleaned up by HttpClient
    @SuppressFBWarnings("OBL_UNSATISFIED_OBLIGATION")
    Optional<InputStream> getBestRecordingForName(String recordingName)
            throws FlightRecorderException, JMXConnectionException, IOException {
        if (super.isAvailable()) {
            Optional<IRecordingDescriptor> currentRecording = getDescriptorByName(recordingName);
            if (currentRecording.isPresent()) {
                return Optional.of(getService().openStream(currentRecording.get(), false));
            }
        }

        Path archivedRecording = recordingsPath.resolve(recordingName);
        if (fs.isRegularFile(archivedRecording) && fs.isReadable(archivedRecording)) {
            return Optional.of(new BufferedInputStream(fs.newInputStream(archivedRecording)));
        }

        return Optional.empty();
    }

    private static class ResponseMessage {
        final StatusLine status;
        final String body;

        ResponseMessage(StatusLine status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    static class RecordingNotFoundException extends Exception {
        private static final long serialVersionUID = 1L;

        RecordingNotFoundException(String recordingName) {
            super(String.format("Recording \"%s\" could not be found", recordingName));
        }
    }
}
