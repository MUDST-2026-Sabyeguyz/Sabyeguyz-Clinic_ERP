package com.sabyeguyz.idcard.agent;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

public final class AgentMain {
    private AgentMain() {}

    public static void main(String[] args) throws Exception {
        String command = args.length == 0 ? "run" : args[0];
        switch (command) {
            case "pair" -> {
                if (args.length != 3) usage();
                AgentConfig config = BackendClient.pair(args[1], args[2]);
                config.save(AgentConfig.defaultPath());
                System.out.println("จับคู่สำเร็จ บันทึกที่ " + AgentConfig.defaultPath());
            }
            case "run" -> run();
            case "read-once" -> {
                CardSource.ReadOutcome outcome = new PcscCardSource().readWhenPresent(Duration.ofSeconds(30), () -> System.out.println("พบบัตรในเครื่องอ่าน กำลังอ่าน…"));
                System.out.println("status=" + outcome.status() + (outcome.message() == null ? "" : " message=" + outcome.message()));
                if (outcome.card() != null) {
                    CardData card = outcome.card();
                    String id = card.nationalId();
                    System.out.println("nationalId=" + id.charAt(0) + "-****-*****-" + id.substring(10, 12) + "-" + id.charAt(12));
                    System.out.println("name=" + card.titleTh() + " " + card.firstNameTh() + " " + card.lastNameTh());
                    System.out.println("birthDate=" + card.birthDate() + " gender=" + card.gender() + " lifetime=" + card.lifetime() + " expiry=" + card.expiryDate());
                    System.out.println("addressLength=" + (card.address() == null ? 0 : card.address().length()));
                }
            }
            case "autostart-install" -> {
                if (args.length != 2) usage();
                Autostart.install(args[1]);
            }
            case "autostart-remove" -> Autostart.remove();
            default -> usage();
        }
    }

    /**
     * Starts the local server for the web page, then resumes the saved pairing (or waits to be paired from the page).
     * Origins the page may call from come from {@code -Dclinic.allowedOrigins=} (set by the installer) or the
     * {@code CLINIC_ID_CARD_ALLOWED_ORIGINS} environment variable; the paired backend is always allowed too.
     */
    private static void run() {
        Set<String> configured = LocalServer.parseOrigins(System.getProperty("clinic.allowedOrigins"),
                System.getenv("CLINIC_ID_CARD_ALLOWED_ORIGINS"));
        AgentRuntime[] holder = new AgentRuntime[1];
        StatusTray tray = StatusTray.create(() -> {
            holder[0].stop();
            System.exit(0);
        }, () -> holder[0].settingsUrl(configured));
        AgentRuntime runtime = new AgentRuntime(AgentConfig.defaultPath(),
                config -> new AgentLoop(new BackendClient(config.backendUrl(), config.token()), new PcscCardSource(), Duration.ofSeconds(30)),
                tray::status);
        holder[0] = runtime;
        try {
            LocalServer server = new LocalServer(LocalServer.DEFAULT_PORT, () -> runtime.allowedOrigins(configured), runtime);
            server.start();
        } catch (IOException unavailable) {
            // a second copy would also poll the backend and split the reads between two programs
            System.err.println("เปิดพอร์ต " + LocalServer.DEFAULT_PORT + " ไม่ได้ อาจมีโปรแกรมตัวกลางเปิดอยู่แล้ว: " + unavailable.getMessage());
            System.exit(1);
        }
        if (configured.isEmpty()) {
            System.err.println("ยังไม่ได้ตั้งเว็บที่อนุญาต (clinic.allowedOrigins) หน้าเว็บจะจับคู่ผ่านปุ่มไม่ได้จนกว่าจะจับคู่ด้วยคำสั่ง pair");
        }
        runtime.start();
        Autostart.installOnFirstRun(AgentConfig.defaultPath().resolveSibling("autostart-installed"));
    }

    private static void usage() {
        System.err.println("""
                ใช้งาน:
                  pair <backendUrl> <code>        จับคู่เครื่องกับ backend ด้วยรหัสจากผู้จัดการ
                  run                              เริ่มทำงาน (ค่าเริ่มต้น)
                  read-once                        ลองอ่านบัตรหนึ่งครั้งและแสดงผล (ปิดบังเลขบัตร)
                  autostart-install <launcher>     เริ่มอัตโนมัติเมื่อเข้าระบบ
                  autostart-remove                 ยกเลิกการเริ่มอัตโนมัติ""");
        System.exit(2);
    }
}
