package com.sabyeguyz.clinicv2.calendar;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Holds the Google service account key a Manager pastes in the UI. The key JSON is stored AES-256-GCM encrypted in
 * {@code calendar_service_account}; the encryption key is derived from the server's JWT secret, so the database alone
 * (a dump, a backup) does not reveal it. Changing the JWT secret makes the stored key unreadable: paste it again.
 * The private key is never returned by any API and never logged.
 */
@Service
public class CalendarCredentialStore {
    private static final int IV_BYTES = 12;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final SecretKeySpec aesKey;
    private final SecureRandom random = new SecureRandom();
    private volatile ServiceAccountCredentials cached;
    private volatile boolean loaded;

    public CalendarCredentialStore(JdbcTemplate jdbc, ObjectMapper json, @Value("${app.jwt.secret}") String secret) {
        this.jdbc = jdbc;
        this.json = json;
        this.aesKey = new SecretKeySpec(sha256("clinic-calendar-credentials:" + secret), "AES");
    }

    /** The stored credentials, or empty when none were saved (or they can no longer be decrypted). */
    public Optional<ServiceAccountCredentials> current() {
        if (!loaded) {
            synchronized (this) {
                if (!loaded) {
                    cached = readFromDatabase();
                    loaded = true;
                }
            }
        }
        return Optional.ofNullable(cached);
    }

    public boolean isConfigured() { return current().isPresent(); }

    public Optional<String> clientEmail() { return current().map(ServiceAccountCredentials::clientEmail); }

    /** Validates the pasted key file and returns its credentials without saving them. */
    public ServiceAccountCredentials parse(String keyJson) {
        return ServiceAccountCredentials.parse(keyJson, json);
    }

    public synchronized void save(ServiceAccountCredentials credentials, String keyJson, Long actorId) {
        String encrypted = encrypt(keyJson);
        jdbc.update("DELETE FROM calendar_service_account");
        jdbc.update("INSERT INTO calendar_service_account (id, client_email, encrypted_key, updated_by) VALUES (1, ?, ?, ?)",
                credentials.clientEmail(), encrypted, actorId);
        cached = credentials;
        loaded = true;
    }

    public synchronized void clear() {
        jdbc.update("DELETE FROM calendar_service_account");
        cached = null;
        loaded = true;
    }

    private ServiceAccountCredentials readFromDatabase() {
        List<String> rows = jdbc.queryForList("SELECT encrypted_key FROM calendar_service_account WHERE id = 1", String.class);
        if (rows.isEmpty()) return null;
        try {
            return ServiceAccountCredentials.parse(decrypt(rows.get(0)), json);
        } catch (RuntimeException unreadable) {
            return null; // e.g. the server secret changed: behaves as "not connected" until the key is pasted again
        }
    }

    private String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(128, iv));
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + sealed.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(sealed, 0, out, iv.length, sealed.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not encrypt the calendar key", exception);
        }
    }

    private String decrypt(String stored) {
        try {
            byte[] in = Base64.getDecoder().decode(stored);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(128, in, 0, IV_BYTES));
            return new String(cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Could not decrypt the calendar key", exception);
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
