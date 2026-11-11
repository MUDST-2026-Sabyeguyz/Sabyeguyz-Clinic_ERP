package com.sabyeguyz.idcard.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Starts the agent when the user logs in, using each system's own per-user mechanism (no administrator rights). */
final class Autostart {
    private static final String NAME = "ClinicIdCardAgent";

    private Autostart() {}

    static String plist(String javaCommandPath, String jarPath) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                  <key>Label</key><string>com.sabyeguyz.idcardagent</string>
                  <key>ProgramArguments</key>
                  <array>
                    <string>%s</string>
                    <string>-jar</string>
                    <string>%s</string>
                    <string>run</string>
                  </array>
                  <key>RunAtLoad</key><true/>
                  <key>KeepAlive</key><true/>
                </dict>
                </plist>
                """.formatted(javaCommandPath, jarPath);
    }

    static String desktopEntry(String execLine) {
        return """
                [Desktop Entry]
                Type=Application
                Name=Clinic ID Card Agent
                Exec=%s
                X-GNOME-Autostart-enabled=true
                """.formatted(execLine);
    }

    static String[] windowsRunCommand(String exe) {
        return new String[] {"reg", "add", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                "/v", NAME, "/t", "REG_SZ", "/d", "\"" + exe + "\" run", "/f"};
    }

    static void install(String launcher) throws IOException, InterruptedException {
        String os = System.getProperty("os.name").toLowerCase();
        Path home = Path.of(System.getProperty("user.home"));
        if (os.contains("win")) {
            run(windowsRunCommand(launcher));
        } else if (os.contains("mac")) {
            Path file = home.resolve("Library/LaunchAgents/com.sabyeguyz.idcardagent.plist");
            Files.createDirectories(file.getParent());
            Files.writeString(file, plist(System.getProperty("java.home") + "/bin/java", launcher));
        } else {
            Path file = home.resolve(".config/autostart/clinic-id-card-agent.desktop");
            Files.createDirectories(file.getParent());
            Files.writeString(file, desktopEntry(launcher + " run"));
        }
    }

    /**
     * First start of an installed Windows build: register for login once, so the installer needs no extra step. Only the
     * installed launcher qualifies (a developer running the jar must not register a path that cannot start on its own),
     * and the marker stops it from undoing an {@code autostart-remove} later.
     */
    static boolean shouldInstallOnFirstRun(String os, String installedLauncher, boolean alreadyDone) {
        return !alreadyDone && os.toLowerCase().contains("win") && installedLauncher != null && !installedLauncher.isBlank();
    }

    static void installOnFirstRun(Path markerFile) {
        String launcher = System.getProperty("jpackage.app-path");
        if (!shouldInstallOnFirstRun(System.getProperty("os.name"), launcher, Files.exists(markerFile))) return;
        try {
            install(launcher);
            Files.createDirectories(markerFile.getParent());
            Files.writeString(markerFile, "done");
        } catch (IOException | InterruptedException failed) {
            if (failed instanceof InterruptedException) Thread.currentThread().interrupt();
            System.err.println("ตั้งให้เริ่มอัตโนมัติไม่สำเร็จ: " + failed.getMessage());
        }
    }

    static void remove() throws IOException, InterruptedException {
        String os = System.getProperty("os.name").toLowerCase();
        Path home = Path.of(System.getProperty("user.home"));
        if (os.contains("win")) {
            run(new String[] {"reg", "delete", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run", "/v", NAME, "/f"});
        } else if (os.contains("mac")) {
            Files.deleteIfExists(home.resolve("Library/LaunchAgents/com.sabyeguyz.idcardagent.plist"));
        } else {
            Files.deleteIfExists(home.resolve(".config/autostart/clinic-id-card-agent.desktop"));
        }
    }

    private static void run(String[] command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).inheritIO().start();
        if (process.waitFor() != 0) throw new IOException("คำสั่งตั้งค่าเริ่มอัตโนมัติไม่สำเร็จ");
    }
}
