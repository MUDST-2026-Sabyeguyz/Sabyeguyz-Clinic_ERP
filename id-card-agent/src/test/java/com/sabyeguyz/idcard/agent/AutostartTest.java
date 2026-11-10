package com.sabyeguyz.idcard.agent;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AutostartTest {
    @Test
    void macLaunchAgentRunsTheAgentAtLogin() {
        String plist = Autostart.plist("/usr/bin/java", "/Applications/ClinicIdCardAgent/id-card-agent.jar");
        assertTrue(plist.contains("<key>RunAtLoad</key>"));
        assertTrue(plist.contains("<string>com.sabyeguyz.idcardagent</string>"));
        assertTrue(plist.contains("<string>/usr/bin/java</string>"));
        assertTrue(plist.contains("<string>run</string>"));
    }

    @Test
    void linuxDesktopEntryStartsTheAgentWithTheSession() {
        String entry = Autostart.desktopEntry("/opt/clinic-id-card-agent/bin/ClinicIdCardAgent run");
        assertTrue(entry.startsWith("[Desktop Entry]"));
        assertTrue(entry.contains("Exec=/opt/clinic-id-card-agent/bin/ClinicIdCardAgent run"));
        assertTrue(entry.contains("X-GNOME-Autostart-enabled=true"));
    }

    @Test
    void windowsUsesTheCurrentUsersRunKey() {
        String[] command = Autostart.windowsRunCommand("C:\\Program Files\\ClinicIdCardAgent\\ClinicIdCardAgent.exe");
        assertEquals("reg", command[0]);
        assertTrue(String.join(" ", command).contains("HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"));
        assertTrue(String.join(" ", command).contains("ClinicIdCardAgent.exe"));
    }
}
