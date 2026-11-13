package com.sabyeguyz.idcard.agent;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ThaiIdCardParserTest {
    static final Charset TIS620 = Charset.forName("TIS-620");

    static byte[] tis(String text) {
        return text.getBytes(TIS620);
    }

    static Map<ThaiIdCardParser.Field, byte[]> sample() {
        Map<ThaiIdCardParser.Field, byte[]> raw = new EnumMap<>(ThaiIdCardParser.Field.class);
        raw.put(ThaiIdCardParser.Field.CID, tis("1101700005119"));
        raw.put(ThaiIdCardParser.Field.NAME_TH, tis("นาย#สมชาย##ใจดี   "));
        raw.put(ThaiIdCardParser.Field.NAME_EN, tis("Mr.#Somchai##Jaidee     "));
        raw.put(ThaiIdCardParser.Field.BIRTH, tis("25400131"));
        raw.put(ThaiIdCardParser.Field.GENDER, tis("1"));
        raw.put(ThaiIdCardParser.Field.ISSUE, tis("25630202"));
        raw.put(ThaiIdCardParser.Field.EXPIRY, tis("99999999"));
        raw.put(ThaiIdCardParser.Field.ADDRESS, tis("88/8#หมู่ที่ 3##ซอยลาดพร้าว 3#ถนนลาดพร้าว#ตำบลจรเข้บัว#อำเภอลาดพร้าว#จังหวัดกรุงเทพมหานคร  "));
        return raw;
    }

    @Test
    void parsesAThaiCardIntoGregorianDatesAndSeparateNames() {
        CardData card = ThaiIdCardParser.parse(sample());
        assertEquals("1101700005119", card.nationalId());
        assertEquals("นาย", card.titleTh());
        assertEquals("สมชาย", card.firstNameTh());
        assertNull(card.middleNameTh());
        assertEquals("ใจดี", card.lastNameTh());
        assertEquals("Somchai Jaidee", card.fullNameEn());
        assertEquals(LocalDate.of(1997, 1, 31), card.birthDate());
        assertEquals("M", card.gender());
        assertEquals(LocalDate.of(2020, 2, 2), card.issueDate());
        assertNull(card.expiryDate());
        assertTrue(card.lifetime());
        assertEquals("88/8 หมู่ที่ 3 ซอยลาดพร้าว 3 ถนนลาดพร้าว ตำบลจรเข้บัว อำเภอลาดพร้าว จังหวัดกรุงเทพมหานคร", card.address());
    }

    @Test
    void keepsAMiddleNameAndAFemaleGenderAndAnExpiryDate() {
        var raw = sample();
        raw.put(ThaiIdCardParser.Field.NAME_TH, tis("นางสาว#สมหญิง#กลาง#ใจดี"));
        raw.put(ThaiIdCardParser.Field.GENDER, tis("2"));
        raw.put(ThaiIdCardParser.Field.EXPIRY, tis("25681231"));
        CardData card = ThaiIdCardParser.parse(raw);
        assertEquals("กลาง", card.middleNameTh());
        assertEquals("F", card.gender());
        assertFalse(card.lifetime());
        assertEquals(LocalDate.of(2025, 12, 31), card.expiryDate());
    }

    @Test
    void trailingPaddingBytesAreIgnored() {
        var raw = sample();
        raw.put(ThaiIdCardParser.Field.CID, new byte[] {'1', '1', '0', '1', '7', '0', '0', '0', '0', '5', '1', '1', '9', 0, 0});
        assertEquals("1101700005119", ThaiIdCardParser.parse(raw).nationalId());
    }

    @Test
    void anAddressLongerThanTheDatabaseColumnIsCut() {
        var raw = sample();
        raw.put(ThaiIdCardParser.Field.ADDRESS, tis("ก".repeat(400)));
        assertEquals(255, ThaiIdCardParser.parse(raw).address().length());
    }

    @Test
    void aNumberThatIsNot13DigitsIsRejected() {
        var raw = sample();
        raw.put(ThaiIdCardParser.Field.CID, tis("12345"));
        assertThrows(IllegalArgumentException.class, () -> ThaiIdCardParser.parse(raw));
    }

    @Test
    void buddhistDatesConvertAndLifetimeIsNull() {
        assertEquals(LocalDate.of(1997, 1, 31), ThaiIdCardParser.buddhistDate("25400131"));
        assertNull(ThaiIdCardParser.buddhistDate("99999999"));
        assertNull(ThaiIdCardParser.buddhistDate("   "));
        assertNull(ThaiIdCardParser.buddhistDate(null));
    }

    @Test
    void aMissingOptionalFieldBecomesNull() {
        var raw = sample();
        raw.remove(ThaiIdCardParser.Field.NAME_EN);
        raw.remove(ThaiIdCardParser.Field.ADDRESS);
        CardData card = ThaiIdCardParser.parse(raw);
        assertNull(card.fullNameEn());
        assertNull(card.address());
    }
}
