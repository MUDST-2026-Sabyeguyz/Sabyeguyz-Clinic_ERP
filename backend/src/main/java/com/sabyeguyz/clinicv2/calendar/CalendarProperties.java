package com.sabyeguyz.clinicv2.calendar;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the two-way Google Calendar sync ({@code app.calendar.*}). The sync is off unless
 * {@code app.calendar.enabled=true}. The OAuth values belong in environment variables on the server
 * (APP_CALENDAR_CLIENT_ID, APP_CALENDAR_CLIENT_SECRET, APP_CALENDAR_REFRESH_TOKEN), never in the repository.
 */
@ConfigurationProperties(prefix = "app.calendar")
public class CalendarProperties {
    private boolean enabled = false;
    private String clientId = "";
    private String clientSecret = "";
    private String refreshToken = "";
    /** Path of the service account JSON key file; when set it is used instead of the OAuth refresh token. */
    private String serviceAccountKeyFile = "";
    /** The schema keeps no appointment end time yet, so every calendar event lasts this long. */
    private int defaultDurationMinutes = 60;
    private String timeZone = "Asia/Bangkok";
    private long pushIntervalMs = 30_000;
    private long pullIntervalMs = 60_000;
    /** On start (and the first poll of a calendar) look back this far for changes made in Google. */
    private int lookbackHours = 24;
    /** app_user id recorded as the author of changes that came from Google Calendar. */
    private long actorUserId = 1;
    private String apiBaseUrl = "https://www.googleapis.com/calendar/v3";
    private String tokenUrl = "https://oauth2.googleapis.com/token";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public String getServiceAccountKeyFile() { return serviceAccountKeyFile; }
    public void setServiceAccountKeyFile(String serviceAccountKeyFile) { this.serviceAccountKeyFile = serviceAccountKeyFile; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public int getDefaultDurationMinutes() { return defaultDurationMinutes; }
    public void setDefaultDurationMinutes(int defaultDurationMinutes) { this.defaultDurationMinutes = defaultDurationMinutes; }
    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
    public long getPushIntervalMs() { return pushIntervalMs; }
    public void setPushIntervalMs(long pushIntervalMs) { this.pushIntervalMs = pushIntervalMs; }
    public long getPullIntervalMs() { return pullIntervalMs; }
    public void setPullIntervalMs(long pullIntervalMs) { this.pullIntervalMs = pullIntervalMs; }
    public int getLookbackHours() { return lookbackHours; }
    public void setLookbackHours(int lookbackHours) { this.lookbackHours = lookbackHours; }
    public long getActorUserId() { return actorUserId; }
    public void setActorUserId(long actorUserId) { this.actorUserId = actorUserId; }
    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }
    public String getTokenUrl() { return tokenUrl; }
    public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }
}
