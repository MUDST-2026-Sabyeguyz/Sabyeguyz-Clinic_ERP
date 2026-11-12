package com.sabyeguyz.clinicv2.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

/** The pasted Google key is validated, stored encrypted (never as plain text) and read back intact. */
class CalendarCredentialStoreTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private static String keyJson(String email) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pair.getPrivate().getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";
        return JSON.writeValueAsString(java.util.Map.of(
                "type", "service_account", "client_email", email, "private_key", pem,
                "token_uri", "https://oauth2.googleapis.com/token"));
    }

    @Test
    void savedKeyIsEncryptedAndReadsBack() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CalendarCredentialStore store = new CalendarCredentialStore(jdbc, JSON, "a-server-secret-of-some-length-123456");
        assertFalse(store.isConfigured());

        String keyJson = keyJson("sync@clinic.iam.gserviceaccount.com");
        ServiceAccountCredentials parsed = store.parse(keyJson);
        assertEquals("sync@clinic.iam.gserviceaccount.com", parsed.clientEmail());
        store.save(parsed, keyJson, 7L);

        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(anyString(), eq("sync@clinic.iam.gserviceaccount.com"), stored.capture(), eq(7L));
        assertFalse(stored.getValue().contains("PRIVATE KEY"), "the private key must not be stored as plain text");
        assertFalse(stored.getValue().contains("client_email"));
        assertTrue(store.isConfigured());

        // a fresh instance (a restart) reads the encrypted row back
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of(stored.getValue()));
        CalendarCredentialStore restarted = new CalendarCredentialStore(jdbc, JSON, "a-server-secret-of-some-length-123456");
        assertEquals("sync@clinic.iam.gserviceaccount.com", restarted.clientEmail().orElseThrow());
        assertTrue(restarted.current().orElseThrow().assertion("scope", java.time.Instant.now()).split("\\.").length == 3);
    }

    @Test
    void aChangedServerSecretMeansNotConnectedInsteadOfAnError() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CalendarCredentialStore store = new CalendarCredentialStore(jdbc, JSON, "first-secret-first-secret-first-secret");
        String keyJson = keyJson("sync@clinic.iam.gserviceaccount.com");
        store.save(store.parse(keyJson), keyJson, 1L);
        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(anyString(), anyString(), stored.capture(), any());

        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of(stored.getValue()));
        CalendarCredentialStore other = new CalendarCredentialStore(jdbc, JSON, "another-secret-another-secret-other");
        assertFalse(other.isConfigured());
    }

    @Test
    void rejectsFilesThatAreNotServiceAccountKeys() {
        CalendarCredentialStore store = new CalendarCredentialStore(mock(JdbcTemplate.class), JSON, "secret-secret-secret-secret-1234");
        assertThrows(CalendarApiException.class, () -> store.parse("not json at all"));
        assertThrows(CalendarApiException.class, () -> store.parse("{\"client_email\":\"a@b.c\"}"));
        assertThrows(CalendarApiException.class,
                () -> store.parse("{\"client_email\":\"a@b.c\",\"private_key\":\"-----BEGIN PRIVATE KEY-----\\nAAAA\\n-----END PRIVATE KEY-----\"}"));
    }
}
