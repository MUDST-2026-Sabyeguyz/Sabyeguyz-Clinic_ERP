package com.sabyeguyz.idcard.agent;

import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Turns the raw bytes of each chip field (TIS-620 text, '#'-separated parts, Buddhist-era dates) into a CardData. */
final class ThaiIdCardParser {
    enum Field { CID, NAME_TH, NAME_EN, BIRTH, GENDER, ISSUE, EXPIRY, ADDRESS }

    private static final Charset TIS620 = Charset.forName("TIS-620");
    private static final int ADDRESS_MAX = 255;

    private ThaiIdCardParser() {}

    static CardData parse(Map<Field, byte[]> raw) {
        String nationalId = text(raw.get(Field.CID));
        if (nationalId == null || !nationalId.matches("[0-9]{13}")) {
            throw new IllegalArgumentException("เลขบัตรอ่านได้ไม่ครบ 13 หลัก");
        }
        String[] th = parts(text(raw.get(Field.NAME_TH)), 4);
        String[] en = parts(text(raw.get(Field.NAME_EN)), 4);
        String expiryText = text(raw.get(Field.EXPIRY));
        boolean lifetime = "99999999".equals(expiryText);
        return new CardData(
                nationalId,
                blankToNull(th[0]), blankToNull(th[1]), blankToNull(th[2]), blankToNull(th[3]),
                joinNonBlank(" ", en[1], en[2], en[3]),
                buddhistDate(text(raw.get(Field.BIRTH))),
                gender(text(raw.get(Field.GENDER))),
                address(text(raw.get(Field.ADDRESS))),
                buddhistDate(text(raw.get(Field.ISSUE))),
                lifetime ? null : buddhistDate(expiryText),
                lifetime);
    }

    static LocalDate buddhistDate(String yyyymmdd) {
        if (yyyymmdd == null || yyyymmdd.isBlank() || "99999999".equals(yyyymmdd.trim())) return null;
        String value = yyyymmdd.trim();
        if (!value.matches("[0-9]{8}")) return null;
        try {
            return LocalDate.of(Integer.parseInt(value.substring(0, 4)) - 543,
                    Integer.parseInt(value.substring(4, 6)), Integer.parseInt(value.substring(6, 8)));
        } catch (java.time.DateTimeException invalid) {
            return null;
        }
    }

    private static String text(byte[] bytes) {
        if (bytes == null) return null;
        String value = new String(bytes, TIS620).replace('\u0000', ' ').strip();
        return value.isEmpty() ? null : value;
    }

    private static String[] parts(String value, int count) {
        String[] out = new String[count];
        java.util.Arrays.fill(out, "");
        if (value == null) return out;
        String[] split = value.split("#", -1);
        for (int i = 0; i < count && i < split.length; i++) out[i] = split[i].strip();
        return out;
    }

    private static String gender(String value) {
        if ("1".equals(value)) return "M";
        if ("2".equals(value)) return "F";
        return null;
    }

    private static String address(String value) {
        if (value == null) return null;
        List<String> pieces = new ArrayList<>();
        for (String piece : value.split("#", -1)) if (!piece.isBlank()) pieces.add(piece.strip());
        String joined = String.join(" ", pieces);
        if (joined.isEmpty()) return null;
        return joined.length() > ADDRESS_MAX ? joined.substring(0, ADDRESS_MAX) : joined;
    }

    private static String joinNonBlank(String separator, String... values) {
        List<String> pieces = new ArrayList<>();
        for (String value : values) if (value != null && !value.isBlank()) pieces.add(value);
        return pieces.isEmpty() ? null : String.join(separator, pieces);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
