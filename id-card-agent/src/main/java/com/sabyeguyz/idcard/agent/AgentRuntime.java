package com.sabyeguyz.idcard.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The running agent: the pairing it holds, and the loop that serves read commands for that pairing.
 * Pairing again (from the web page or the command line) replaces the previous pairing and restarts the loop.
 */
final class AgentRuntime implements LocalServer.Controller {
    static final String VERSION = "1.0.0";
    static final String SETTINGS_PATH = "/staff/settings/card-readers";

    interface LoopFactory {
        AgentLoop create(AgentConfig config);
    }

    private final Path configPath;
    private final LoopFactory loops;
    private final Consumer<String> onStatus;
    private volatile AgentConfig config;
    private volatile boolean revoked;
    private AgentLoop loop;
    private Thread thread;

    AgentRuntime(Path configPath, LoopFactory loops, Consumer<String> onStatus) {
        this.configPath = configPath;
        this.loops = loops;
        this.onStatus = onStatus;
    }

    /** Resumes the saved pairing, if there is one. Otherwise waits to be paired. */
    synchronized void start() {
        if (Files.exists(configPath)) {
            try {
                config = AgentConfig.load(configPath);
            } catch (IOException unreadable) {
                onStatus.accept("อ่านไฟล์ตั้งค่าไม่ได้ ต้องจับคู่ใหม่");
                return;
            }
            startLoop();
        } else {
            onStatus.accept("ยังไม่ได้จับคู่ เปิดหน้าตั้งค่าเครื่องอ่านบัตรในระบบเพื่อจับคู่");
        }
    }

    synchronized void stop() {
        stopLoop();
    }

    @Override
    public LocalServer.Status status() {
        AgentConfig current = config;
        boolean paired = current != null && !revoked;
        return new LocalServer.Status(paired, paired ? current.workstationName() : null, paired ? current.branchCode() : null,
                paired ? current.workstationId() : null, VERSION);
    }

    @Override
    public LocalServer.Status pair(String backendUrl, String code) throws IOException {
        AgentConfig paired = BackendClient.pair(backendUrl, code);
        paired.save(configPath);
        synchronized (this) {
            stopLoop();
            config = paired;
            startLoop();
        }
        return status();
    }

    /** Origins the web page may call from: the ones the clinic set at install time, plus the backend this agent is paired with. */
    Set<String> allowedOrigins(Set<String> configured) {
        Set<String> all = new LinkedHashSet<>(configured);
        AgentConfig current = config;
        if (current != null) {
            String paired = LocalServer.normalizeOrigin(current.backendUrl());
            if (paired != null) all.add(paired);
        }
        return all;
    }

    /** The page in the web app where readers are managed, when the agent knows where the system is. */
    Optional<String> settingsUrl(Set<String> configured) {
        AgentConfig current = config;
        String origin = current == null ? null : LocalServer.normalizeOrigin(current.backendUrl());
        if (origin == null) origin = configured.stream().findFirst().orElse(null);
        return Optional.ofNullable(origin).map(base -> base + SETTINGS_PATH);
    }

    private void startLoop() {
        revoked = false;
        AgentLoop next = loops.create(config);
        next.onStatus(onStatus);
        Thread runner = new Thread(() -> {
            next.run();
            if (next.wasRevoked()) markRevoked(next);
        }, "agent-loop");
        loop = next;
        thread = runner;
        runner.start();
    }

    private synchronized void markRevoked(AgentLoop ended) {
        if (loop == ended) revoked = true;
    }

    private void stopLoop() {
        if (loop != null) loop.stop();
        // a waiting long-poll would otherwise keep serving the old pairing for up to 40 seconds
        if (thread != null) thread.interrupt();
        loop = null;
        thread = null;
    }
}
