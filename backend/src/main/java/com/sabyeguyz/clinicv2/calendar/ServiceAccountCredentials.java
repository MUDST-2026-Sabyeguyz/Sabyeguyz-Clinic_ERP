package com.sabyeguyz.clinicv2.calendar;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A Google service account (the robot account of a Google Cloud project): its e-mail, private key and token address
 * come from the JSON key file downloaded in the Cloud console. It signs a short-lived JWT that Google exchanges for
 * an access token, so no consent screen, no "publish app" and no refresh token that can expire are involved.
 * The calendars only have to be shared with the service account's e-mail ("Make changes to events").
 */
public final class ServiceAccountCredentials {
    private final String clientEmail;
    private final PrivateKey privateKey;
    private final String tokenUri;

    ServiceAccountCredentials(
            String clientEmail,
            PrivateKey privateKey,
            String tokenUri) {
        this.clientEmail = clientEmail;
        this.privateKey = privateKey;
        this.tokenUri = tokenUri;
    }

    /** Reads the JSON key file; the private key is never logged. */
    public static ServiceAccountCredentials load(
            Path keyFile,
            ObjectMapper json) {
        try {
            return parse(Files.readString(keyFile, StandardCharsets.UTF_8), json);
        } catch (IOException exception) {
            throw new CalendarApiException("The service account key file cannot be read", exception);
        }
    }

    /** Parses the JSON text of a key file; the private key is never logged. */
    public static ServiceAccountCredentials parse(
            String keyJson,
            ObjectMapper json) {
        try {
            JsonNode key = json.readTree(keyJson);
            String email = key.path("client_email").asText("");
            String pem = key.path("private_key").asText("");

            if (email.isBlank() || pem.isBlank()) {
                throw new CalendarApiException(0, "The service account key file has no client_email / private_key");
            }

            String uri = key.path("token_uri").asText("https://oauth2.googleapis.com/token");

            return new ServiceAccountCredentials(email, privateKeyFrom(pem), uri);
        } catch (IOException | GeneralSecurityException | IllegalArgumentException exception) {
            throw new CalendarApiException("The service account key cannot be read", exception);
        }
    }

    static PrivateKey privateKeyFrom(String pem) throws GeneralSecurityException {
        String base64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        return KeyFactory.getInstance("RSA").generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }

    public String clientEmail() {
        return clientEmail;
    }

    public String tokenUri() {
        return tokenUri;
    }

    /** The signed JWT (RS256) to send to the token address as the "assertion". */
    public String assertion(String scope, Instant now) {
        try {
            Base64.Encoder url = Base64.getUrlEncoder().withoutPadding();

            String header = url.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}"
                    .getBytes(StandardCharsets.UTF_8));

            String claims = url.encodeToString(("{\"iss\":\"" + clientEmail
                    + "\",\"scope\":\"" + scope
                    + "\",\"aud\":\"" + tokenUri
                    + "\",\"iat\":" + now.getEpochSecond()
                    + ",\"exp\":" + (now.getEpochSecond() + 3600) + "}")
                    .getBytes(StandardCharsets.UTF_8));

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));

            return header + "." + claims + "." + url.encodeToString(signature.sign());
        } catch (GeneralSecurityException exception) {
            throw new CalendarApiException("Could not sign the service account assertion", exception);
        }
    }
}
