package com.sabyeguyz.idcard.agent;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ThaiIdCardReaderTest {
    static final Charset TIS620 = Charset.forName("TIS-620");

    /** A card that answers each command from a table; commands it has not been told about answer 6A 82. */
    static final class FakeCard implements ApduChannel {
        final Map<String, byte[]> answers = new HashMap<>();
        final Map<String, byte[]> pendingResponse = new HashMap<>();
        final List<String> log = new ArrayList<>();
        boolean useGetResponse = false;

        @Override
        public Response transmit(byte[] command) {
            String key = HexFormat.of().formatHex(command).toUpperCase();
            log.add(key);
            if (key.startsWith("00C00000")) { // GET RESPONSE
                byte[] data = pendingResponse.remove("last");
                return new Response(data, 0x9000);
            }
            byte[] data = answers.get(key);
            if (data == null) return new Response(new byte[0], 0x6A82);
            if (key.startsWith("00A40400")) return new Response(new byte[0], 0x9000);
            if (useGetResponse) {
                pendingResponse.put("last", data);
                return new Response(new byte[0], 0x6100 | data.length);
            }
            return new Response(data, 0x9000);
        }
    }

    static FakeCard thaiCard() {
        FakeCard card = new FakeCard();
        card.answers.put("00A4040008A000000054480001", new byte[0]);
        card.answers.put("80B0000402000D", "1101700005119".getBytes(TIS620));
        card.answers.put("80B00011020064", "นาย#สมชาย##ใจดี".getBytes(TIS620));
        card.answers.put("80B00075020064", "Mr.#Somchai##Jaidee".getBytes(TIS620));
        card.answers.put("80B000D9020008", "25400131".getBytes(TIS620));
        card.answers.put("80B000E1020001", "1".getBytes(TIS620));
        card.answers.put("80B00167020008", "25630202".getBytes(TIS620));
        card.answers.put("80B0016F020008", "99999999".getBytes(TIS620));
        card.answers.put("80B01579020064", "88/8#หมู่ที่ 3#ตำบลจรเข้บัว".getBytes(TIS620));
        return card;
    }

    @Test
    void readsEveryFieldAfterSelectingTheCardApplet() throws IOException {
        FakeCard card = thaiCard();
        CardData data = new ThaiIdCardReader().read(card);
        assertEquals("1101700005119", data.nationalId());
        assertEquals("สมชาย", data.firstNameTh());
        assertEquals("Somchai Jaidee", data.fullNameEn());
        assertTrue(data.lifetime());
        assertEquals("00A4040008A000000054480001", card.log.get(0), "the applet is selected first");
    }

    @Test
    void followsAMoreDataAnswerWithGetResponse() throws IOException {
        FakeCard card = thaiCard();
        card.useGetResponse = true;
        CardData data = new ThaiIdCardReader().read(card);
        assertEquals("1101700005119", data.nationalId());
        assertTrue(card.log.stream().anyMatch(c -> c.startsWith("00C00000")));
    }

    @Test
    void failsWhenTheCardIsNotAThaiIdCard() {
        FakeCard card = new FakeCard();
        IOException error = assertThrows(IOException.class, () -> new ThaiIdCardReader().read(card));
        assertTrue(error.getMessage().contains("บัตรประชาชน"));
    }

    @Test
    void failsWhenAFieldCannotBeRead() {
        FakeCard card = thaiCard();
        card.answers.remove("80B01579020064");
        assertThrows(IOException.class, () -> new ThaiIdCardReader().read(card));
    }
}
