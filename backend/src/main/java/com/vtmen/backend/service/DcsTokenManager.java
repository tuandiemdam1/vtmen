package com.vtmen.backend.service;

import com.vtmen.backend.config.DcsApiProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages the DCS auth token.
 * Logs in via POST /api/auth/login with JSON body {"username":…,"password":…}.
 * Caches the returned token for 23 hours and refreshes automatically on expiry or 401.
 */
@Service
public class DcsTokenManager {

    private static final Logger log = LoggerFactory.getLogger(DcsTokenManager.class);
    /** Refresh 1 hour before the 24-hour expiry. */
    private static final long TOKEN_TTL_SECONDS = 23 * 60 * 60;

    @Autowired
    private DcsApiProperties dcsApiProperties;

    @Autowired
    private RestClient robotRestClient;

    private volatile String cachedToken = null;
    private volatile Instant expiresAt = Instant.EPOCH;
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Returns a valid DCS token, logging in automatically if needed.
     * Returns {@code null} if credentials are not configured or login fails.
     */
    public String getToken() {
        if (cachedToken != null && Instant.now().isBefore(expiresAt)) {
            return cachedToken;
        }
        lock.lock();
        try {
            if (cachedToken != null && Instant.now().isBefore(expiresAt)) {
                return cachedToken;
            }
            return login();
        } finally {
            lock.unlock();
        }
    }

    /** Force a fresh login (call after receiving 401 from DCS). */
    public String refreshToken() {
        lock.lock();
        try {
            cachedToken = null;
            expiresAt = Instant.EPOCH;
            return login();
        } finally {
            lock.unlock();
        }
    }

    private String login() {
        String username = dcsApiProperties.getUsername();
        String password = dcsApiProperties.getPassword();

        if (username == null || username.isBlank()) {
            log.warn("[DcsToken] DCS_USERNAME not configured – requests will be sent without auth.");
            return null;
        }

        String base = buildBaseUrl(dcsApiProperties.getLocationsUrl());
        String loginUrl = base + "/api/auth/login";

        try {
            // POST {"username":"…","password":"…"}
            LoginResponse resp = robotRestClient.post()
                    .uri(loginUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("username", username, "password", password))
                    .retrieve()
                    .body(LoginResponse.class);

            if (resp != null && resp.token != null && !resp.token.isBlank()) {
                cachedToken = resp.token;
                expiresAt = Instant.now().plusSeconds(TOKEN_TTL_SECONDS);
                log.info("[DcsToken] Login OK. Token cached 23h. (user={}, status={})",
                        username, resp.status);
                return cachedToken;
            } else {
                String msg = (resp != null) ? resp.message : "null response";
                log.error("[DcsToken] DCS login failed: {} (user={})", msg, username);
                cachedToken = null;
                return null;
            }
        } catch (RestClientException e) {
            log.error("[DcsToken] DCS login request error: {}", e.getMessage());
            cachedToken = null;
            return null;
        }
    }

    /** Extract scheme://host:port from a full URL. */
    private static String buildBaseUrl(String url) {
        if (url == null) return "";
        int pathStart = url.indexOf('/', url.indexOf("://") + 3);
        return pathStart > 0 ? url.substring(0, pathStart) : url;
    }

    // ---- Response DTO (matches DCS /api/auth/login JSON) ----
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class LoginResponse {
        public String token;
        public String status;   // "SUCCESS" on success
        public String message;
        public String username;
    }
}
