package com.redhat.rhjmc.containerjfr.sys;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class FileSystem {

    public boolean isDirectory(Path path) {
        return Files.isDirectory(path);
    }

    public long copy(InputStream in, Path out, CopyOption... copyOptions) throws IOException {
        return Files.copy(in, out, copyOptions);
    }

    public List<String> listDirectoryChildren(Path path) throws IOException {
        return Files.list(path).map(p -> p.getFileName().toString()).collect(Collectors.toList());
    }

    public boolean deleteIfExists(Path path) throws IOException {
        return Files.deleteIfExists(path);
    }

}