package com.redhat.rhjmc.containerjfr.commands.internal;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;

import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@Singleton
class UploadRecordingCommand extends AbstractConnectedCommand implements SerializableCommand {

    private final ClientWriter cw;
    private final FileSystem fs;
    private final Path recordingsPath;
    private final Provider<CloseableHttpClient> httpClientProvider;

    @Inject
    UploadRecordingCommand(ClientWriter cw, FileSystem fs, @Named("RECORDINGS_PATH") Path recordingsPath, Provider<CloseableHttpClient> httpClientProvider) {
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
                    "body", response.body
                )
            );
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

        HttpPost post = new HttpPost(uploadUrl);
        post.setEntity(
            MultipartEntityBuilder.create()
                .addBinaryBody("file", recording.get(), ContentType.APPLICATION_OCTET_STREAM, recordingName)
                .build()
        );

        try (
            CloseableHttpClient httpClient = httpClientProvider.get();
            CloseableHttpResponse response = httpClient.execute(post)
        ) {
            return new ResponseMessage(response.getStatusLine(), EntityUtils.toString(response.getEntity()));
        }
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 2) {
            cw.println("Expected two arguments: recording name and upload URL");
            return false;
        }
        if (!validateRecordingName(args[0])) {
            cw.println("%s is an invalid recording name");
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
    Optional<InputStream> getBestRecordingForName(String recordingName) throws FlightRecorderException, JMXConnectionException, FileNotFoundException {
        if (super.isAvailable()) {
            Optional<IRecordingDescriptor> currentRecording = getDescriptorByName(recordingName);
            if (currentRecording.isPresent()) {
                return Optional.of(getService().openStream(currentRecording.get(), true));
            }
        }

        File archivedRecording = recordingsPath.resolve(recordingName).toFile();
        if (archivedRecording.isFile()) {
            return Optional.of(new FileInputStream(archivedRecording));
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
            super(String.format("Recording \"%s\" could not be found"));
        }
    }

}
