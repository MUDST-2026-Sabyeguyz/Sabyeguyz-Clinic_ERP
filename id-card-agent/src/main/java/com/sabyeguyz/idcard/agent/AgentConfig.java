package com.sabyeguyz.idcard.agent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Where this counter's agent talks to, the token it was given when it was paired, and which reader it is.
 * Files written before the name, branch and id were stored still load (those fields are then null).
 */
record AgentConfig(String backendUrl, String token, String workstationName, String branchCode, Long workstationId) {
    private static final ObjectMapper JSON = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @JsonCreator
    AgentConfig {}

    AgentConfig(String backendUrl, String token) {
        this(backendUrl, token, null, null, null);
    }

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
