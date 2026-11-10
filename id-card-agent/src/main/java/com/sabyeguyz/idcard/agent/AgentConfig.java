package com.sabyeguyz.idcard.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

/** Where this counter's agent talks to, and the token it was given when it was paired. */
record AgentConfig(String backendUrl, String token) {
    private static final ObjectMapper JSON = new ObjectMapper();

    static Path defaultPath() {
        return Path.of(System.getProperty("user.home"), ".clinic-id-card-agent", "config.json");
    }

    static AgentConfig load(Path path) throws IOException {
        return JSON.readValue(path.toFile(), AgentConfig.class);
    }

    void save(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), this);
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException windows) {
            // no POSIX permissions on Windows: the file stays in the user's profile folder
        }
    }
}
