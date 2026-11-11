package com.sabyeguyz.idcard.agent;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import javax.smartcardio.TerminalFactory;

/** The system's PC/SC service (winscard on Windows, PCSC.framework on macOS, pcscd on Linux). */
final class PcscCardSource implements CardSource {
    private final ThaiIdCardReader reader = new ThaiIdCardReader();

    @Override
    public ReadOutcome readWhenPresent(Duration wait, Runnable onCardPresent) {
        List<CardTerminal> terminals;
        try {
            terminals = TerminalFactory.getDefault().terminals().list();
        } catch (CardException | RuntimeException noService) {
            return ReadOutcome.of(Status.NO_READER, "ไม่พบบริการอ่านสมาร์ตการ์ดของระบบ (Linux: ติดตั้ง pcscd)");
        }
        if (terminals.isEmpty()) return ReadOutcome.of(Status.NO_READER, "ไม่พบเครื่องอ่านบัตร");
        long deadline = System.nanoTime() + wait.toNanos();
        try {
            while (System.nanoTime() < deadline) {
                for (CardTerminal terminal : terminals) {
                    if (terminal.isCardPresent()) {
                        onCardPresent.run();
                        return readFrom(terminal);
                    }
                }
                Thread.sleep(300);
            }
            return ReadOutcome.of(Status.NO_CARD, "ไม่พบบัตร");
        } catch (CardException failure) {
            return ReadOutcome.of(Status.ERROR, "เครื่องอ่านบัตรผิดพลาด");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return ReadOutcome.of(Status.ERROR, "ถูกยกเลิก");
        }
    }

    private ReadOutcome readFrom(CardTerminal terminal) {
        try {
            Card card = terminal.connect("*");
            try {
                CardChannel channel = card.getBasicChannel();
                return ReadOutcome.ok(reader.read(command -> {
                    try {
                        ResponseAPDU response = channel.transmit(new CommandAPDU(command));
                        return new ApduChannel.Response(response.getData(), response.getSW());
                    } catch (CardException failure) {
                        throw new IOException("สื่อสารกับบัตรไม่ได้", failure);
                    }
                }));
            } finally {
                card.disconnect(false);
            }
        } catch (IOException | CardException failure) {
            return ReadOutcome.of(Status.ERROR, failure.getMessage() == null ? "อ่านบัตรไม่สำเร็จ" : failure.getMessage());
        }
    }
}
