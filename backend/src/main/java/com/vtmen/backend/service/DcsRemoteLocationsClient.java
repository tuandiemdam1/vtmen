package com.vtmen.backend.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vtmen.backend.config.DcsApiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.List;

@Service
public class DcsRemoteLocationsClient {

    private static final Logger log = LoggerFactory.getLogger(DcsRemoteLocationsClient.class);

    private static final MediaType JSON_UTF8 =
            new MediaType("application", "json", java.nio.charset.StandardCharsets.UTF_8);

    @Autowired
    private RestClient robotRestClient;

    @Autowired
    private DcsApiProperties dcsApiProperties;

    @Autowired
    private com.vtmen.backend.config.RobotTaskProperties robotTaskProperties;

    @Autowired
    private DcsTokenManager dcsTokenManager;

    // GET {@code /task/interface/getParkPointPage} using configured siteId.
    public List<DcsCampusPoint> fetchCampusPoints() {
        return fetchCampusPoints(null);
    }

    public DcsLocationsApiDtos.LocationsEnvelope fetchLocationsEnvelope(String siteIdParam) {
        String url = dcsApiProperties.getLocationsUrl() + "?current=1&size=1000";
        if (siteIdParam != null && !siteIdParam.isBlank() && !siteIdParam.equals("Trường đại học")) {
            url += "&siteId=" + siteIdParam;
        }
        return doGetWithTokenRetry(url, DcsLocationsApiDtos.LocationsEnvelope.class);
    }

    public DcsLocationsApiDtos.SiteEnvelope fetchSites() {
        String baseUrl = robotTaskProperties.getDispatchUrl();
        int pathIndex = baseUrl.indexOf("/", baseUrl.indexOf("://") + 3);
        if (pathIndex > 0) {
            baseUrl = baseUrl.substring(0, pathIndex);
        }
        String url = baseUrl + "/task/interface/getSitePage?current=1&size=1000";
        return doGetWithTokenRetry(url, DcsLocationsApiDtos.SiteEnvelope.class);
    }

    // GET from DCS and convert records to DcsCampusPoint
    public List<DcsCampusPoint> fetchCampusPoints(String mapName) {
        try {
            DcsLocationsApiDtos.LocationsEnvelope env = fetchLocationsEnvelope(mapName);
            if (env == null || env.data() == null || env.data().records() == null) {
                return Collections.emptyList();
            }
            return env.data().records().stream()
                    .map(DcsLocationsApiDtos.ParkPointRecord::toCampusPoint)
                    .filter(p -> p.name() != null && !p.name().isBlank()
                             && p.address() != null && !p.address().isBlank())
                    .toList();
        } catch (RestClientException e) {
            throw new IllegalStateException("DCS locations API failed: " + e.getMessage(), e);
        }
    }

    /**
     * Performs a GET request with the current DCS token.
     * If DCS returns 401, refreshes the token once and retries.
     */
    private <T> T doGetWithTokenRetry(String url, Class<T> responseType) {
        String token = dcsTokenManager.getToken();
        try {
            return doGet(url, token, responseType);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                log.warn("[DcsClient] Got 401 – refreshing token and retrying: {}", url);
                token = dcsTokenManager.refreshToken();
                return doGet(url, token, responseType);
            }
            throw e;
        }
    }

    private <T> T doGet(String url, String token, Class<T> responseType) {
        var req = robotRestClient.get().uri(url).accept(JSON_UTF8);
        if (token != null && !token.isBlank()) {
            req = req.header("token", token);
        }
        ResponseEntity<T> entity = req.retrieve().toEntity(responseType);
        return entity.getBody();
    }
}

