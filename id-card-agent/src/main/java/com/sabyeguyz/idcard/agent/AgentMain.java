package com.sabyeguyz.idcard.agent;

import java.time.Duration;

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
            case "run" -> {
                AgentConfig config = AgentConfig.load(AgentConfig.defaultPath());
                AgentLoop loop = new AgentLoop(new BackendClient(config.backendUrl(), config.token()), new PcscCardSource(), Duration.ofSeconds(30));
                StatusTray tray = StatusTray.create(() -> {
                    loop.stop();
                    System.exit(0);
                });
                loop.onStatus(tray::status);
                loop.run();
            }
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
