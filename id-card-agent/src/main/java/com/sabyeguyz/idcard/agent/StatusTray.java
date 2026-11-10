package com.sabyeguyz.idcard.agent;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;

/** A tray icon whose tooltip shows the agent's state. Where there is no tray (headless Linux) it does nothing. */
final class StatusTray {
    private final TrayIcon icon;

    private StatusTray(TrayIcon icon) {
        this.icon = icon;
    }

    static StatusTray create(Runnable onExit) {
        if (java.awt.GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) return new StatusTray(null);
        Image image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = ((BufferedImage) image).createGraphics();
        g.setColor(new java.awt.Color(0x1E, 0x80, 0x7C));
        g.fillOval(1, 1, 14, 14);
        g.dispose();
        PopupMenu menu = new PopupMenu();
        MenuItem exit = new MenuItem("ปิดโปรแกรมตัวกลาง");
        exit.addActionListener(event -> onExit.run());
        menu.add(exit);
        TrayIcon icon = new TrayIcon(image, "ตัวกลางเครื่องอ่านบัตร", menu);
        try {
            SystemTray.getSystemTray().add(icon);
        } catch (AWTException unavailable) {
            return new StatusTray(null);
        }
        return new StatusTray(icon);
    }

    void status(String text) {
        if (icon != null) icon.setToolTip("ตัวกลางเครื่องอ่านบัตร: " + text);
        System.out.println(text);
    }
}
