package com.vtmen.backend.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vtmen.backend.config.DcsApiProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.List;

@Service
public class DcsRemoteLocationsClient {

    private static final MediaType JSON_UTF8 =
            new MediaType("application", "json", java.nio.charset.StandardCharsets.UTF_8);

    @Autowired
    private RestClient robotRestClient;

    @Autowired
    private DcsApiProperties dcsApiProperties;

    @Autowired
    private com.vtmen.backend.config.RobotTaskProperties robotTaskProperties;

     // GET {@code /task/interface/getParkPointPage} using configured siteId.
    public List<DcsCampusPoint> fetchCampusPoints() {
        return fetchCampusPoints(null);
    }
    public DcsLocationsApiDtos.LocationsEnvelope fetchLocationsEnvelope(String siteIdParam) {
        String url = dcsApiProperties.getLocationsUrl() + "?current=1&size=1000";
        if (siteIdParam != null && !siteIdParam.isBlank() && !siteIdParam.equals("Trường đại học")) {
            url += "&siteId=" + siteIdParam;
        }

        var entity = robotRestClient.get()
                .uri(url)
                .accept(JSON_UTF8)
                .retrieve()
                .toEntity(DcsLocationsApiDtos.LocationsEnvelope.class);
        return entity.getBody();
    }

    public DcsLocationsApiDtos.SiteEnvelope fetchSites() {
        String baseUrl = robotTaskProperties.getDispatchUrl();
        int pathIndex = baseUrl.indexOf("/", baseUrl.indexOf("://") + 3);
        if (pathIndex > 0) {
            baseUrl = baseUrl.substring(0, pathIndex);
        }
        String url = baseUrl + "/task/interface/getSitePage?current=1&size=1000";

        var entity = robotRestClient.get()
                .uri(url)
                .accept(JSON_UTF8)
                .retrieve()
                .toEntity(DcsLocationsApiDtos.SiteEnvelope.class);
        return entity.getBody();
    }
     // GET from DCS and convert records to DcsCampusPoint
     // @param mapName DCS map id; not really used anymore since we filter by siteId, but kept for compatibility.
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
}
