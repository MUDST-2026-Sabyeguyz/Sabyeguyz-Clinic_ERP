package com.sabyeguyz.idcard.agent;

import java.io.IOException;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * Reads the Thai national ID card applet. The read commands are the ones commonly used for this card; confirm them on a
 * real card (see docs/testing for the device check) before relying on them.
 */
final class ThaiIdCardReader {
    private static final byte[] SELECT_APPLET = hex("00A4040008A000000054480001");

    private static final Map<ThaiIdCardParser.Field, byte[]> READ = new EnumMap<>(ThaiIdCardParser.Field.class);

    static {
        READ.put(ThaiIdCardParser.Field.CID, hex("80B0000402000D"));
        READ.put(ThaiIdCardParser.Field.NAME_TH, hex("80B00011020064"));
        READ.put(ThaiIdCardParser.Field.NAME_EN, hex("80B00075020064"));
        READ.put(ThaiIdCardParser.Field.BIRTH, hex("80B000D9020008"));
        READ.put(ThaiIdCardParser.Field.GENDER, hex("80B000E1020001"));
        READ.put(ThaiIdCardParser.Field.ISSUE, hex("80B00167020008"));
        READ.put(ThaiIdCardParser.Field.EXPIRY, hex("80B0016F020008"));
        READ.put(ThaiIdCardParser.Field.ADDRESS, hex("80B01579020064"));
    }

    static byte[] hex(String value) {
        return HexFormat.of().parseHex(value);
    }

    CardData read(ApduChannel channel) throws IOException {
        ApduChannel.Response selected = channel.transmit(SELECT_APPLET);
        if (!ok(selected.sw())) throw new IOException("ไม่ใช่บัตรประชาชนไทย หรืออ่านแอปเพล็ตของบัตรไม่ได้");
        Map<ThaiIdCardParser.Field, byte[]> raw = new EnumMap<>(ThaiIdCardParser.Field.class);
        for (Map.Entry<ThaiIdCardParser.Field, byte[]> field : READ.entrySet()) {
            raw.put(field.getKey(), readField(channel, field.getValue(), field.getKey()));
        }
        try {
            return ThaiIdCardParser.parse(raw);
        } catch (IllegalArgumentException invalid) {
            throw new IOException(invalid.getMessage(), invalid);
        }
    }

    private static byte[] readField(ApduChannel channel, byte[] command, ThaiIdCardParser.Field field) throws IOException {
        ApduChannel.Response response = channel.transmit(command);
        if ((response.sw() & 0xFF00) == 0x6100) { // more data: ask for it
            response = channel.transmit(new byte[] {0x00, (byte) 0xC0, 0x00, 0x00, (byte) (response.sw() & 0xFF)});
        }
        if (response.sw() != 0x9000) throw new IOException("อ่านข้อมูล " + field + " จากบัตรไม่ได้");
        return response.data();
    }

    private static boolean ok(int sw) {
        return sw == 0x9000 || (sw & 0xFF00) == 0x6100;
    }
}
