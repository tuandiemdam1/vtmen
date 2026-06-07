package com.vtmen.backend.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// Wire format for DCS POST {@code /api/dcs/locations} (and our GET proxy {@code /api/maps/dcs}).
public final class DcsLocationsApiDtos {
    private DcsLocationsApiDtos() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LocationsEnvelope(
            Integer code,
            String msg,
            LocationsData data
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LocationsData(
            Integer total,
            List<ParkPointRecord> records
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ParkPointRecord(
            String parkPointId,
            String dockName,
            String dockShowName,
            Double longitude,
            Double latitude
    ) {
        public DcsCampusPoint toCampusPoint() {
            String addr = (dockShowName != null && !dockShowName.isBlank()) ? dockShowName : dockName;
            return new DcsCampusPoint(
                    parkPointId,
                    addr,
                    new DcsCampusPoint.Coordinates(longitude, latitude),
                    null
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SiteEnvelope(
            Integer code,
            String msg,
            SiteData data
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SiteData(
            Integer total,
            List<SiteRecord> records
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SiteRecord(
            String siteId,
            String siteName,
            Double longitude,
            Double latitude,
            Integer siteStatus
    ) {}
}
