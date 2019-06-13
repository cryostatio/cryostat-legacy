package es.andrewazor.containertest.commands.internal;

import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import es.andrewazor.containertest.commands.SerializableCommand;
import es.andrewazor.containertest.net.RecordingExporter;
import es.andrewazor.containertest.tui.ClientWriter;

@Singleton
class ListSavedRecordingsCommand implements SerializableCommand {

    private final ClientWriter cw;
    private final Path recordingsPath;
    private final RecordingExporter exporter;

    @Inject
    ListSavedRecordingsCommand(ClientWriter cw, @Named("RECORDINGS_PATH") Path recordingsPath,
            RecordingExporter exporter) {
        this.cw = cw;
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
        String[] saved = recordingsPath.toFile().list();
        if (saved == null || saved.length == 0) {
            cw.println("\tNone");
            return;
        }
        for (String file : saved) {
            cw.println(String.format("\t%s", file));
        }
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        String[] saved = recordingsPath.toFile().list();
        if (saved == null) {
            return new ListOutput<SavedRecordingDescriptor>(Collections.emptyList());
        }
        List<SavedRecordingDescriptor> recordings = new ArrayList<>(saved.length);
        for (String name : saved) {
            try {
                recordings.add(new SavedRecordingDescriptor(name, exporter.getDownloadURL(name)));
            } catch (UnknownHostException | MalformedURLException | SocketException e) {
                e.printStackTrace();
            }
        }
        return new ListOutput<SavedRecordingDescriptor>(recordings);
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 0) {
            cw.println("No arguments expected");
            return false;
        }
        return true;
    }

    @Override
    public boolean isAvailable() {
        return Files.isDirectory(recordingsPath);
    }

    static class SavedRecordingDescriptor {

        private final String name;
        private final String downloadUrl;

        SavedRecordingDescriptor(String name, String downloadUrl) {
            this.name = name;
            this.downloadUrl = downloadUrl;
        }

        String getName() {
            return this.name;
        }

        String getDownloadUrl() {
            return this.downloadUrl;
        }

        @Override
        public String toString() {
            return ToStringBuilder.reflectionToString(this);
        }

        @Override
        public int hashCode() {
            return HashCodeBuilder.reflectionHashCode(this);
        }

        @Override
        public boolean equals(Object o) {
            return EqualsBuilder.reflectionEquals(this, o);
        }
    }

}
