package com.vtmen.backend.controller;

import com.vtmen.backend.service.CampusMapService;
import com.vtmen.backend.service.DcsCampusPoint;
import com.vtmen.backend.service.DcsLocationsApiDtos;
import com.vtmen.backend.service.DcsRemoteLocationsClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
 // Read campus POIs from Mongo {@code maps} (fed by DCS sync or seed).
@RestController
@RequestMapping("/api/maps")
public class MapController {

    @Autowired
    private CampusMapService campusMapService;

    @Autowired
    private DcsRemoteLocationsClient dcsRemoteLocationsClient;

    @GetMapping("/names")
    public List<String> mapNames() {
        return campusMapService.listMapNames();
    }
     // Live DCS map POIs (server POSTs to {@code vtmen.dcs.locations-url} with {@code map_name}).
     // Response shape matches DCS: {@code status}, {@code message}, {@code data.total_items}, {@code data.points[]}
     // ({@code name}, {@code address}, {@code coordinates.lng/lat}, {@code status}).
    @GetMapping("/dcs")
    public ResponseEntity<DcsLocationsApiDtos.LocationsEnvelope> dcsLive(
            @RequestParam(value = "map_name", required = false) String mapName
    ) {
        try {
            DcsLocationsApiDtos.LocationsEnvelope env = dcsRemoteLocationsClient.fetchLocationsEnvelope(mapName);
            if (env == null) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
            }
            return ResponseEntity.ok(env);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    @GetMapping("/sites")
    public ResponseEntity<DcsLocationsApiDtos.SiteEnvelope> dcsSites() {
        try {
            DcsLocationsApiDtos.SiteEnvelope env = dcsRemoteLocationsClient.fetchSites();
            if (env == null) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
            }
            return ResponseEntity.ok(env);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    @GetMapping("/points")
    public List<MapPointResponse> points(
            @RequestParam(value = "map_name", required = false) String mapName
    ) {
        List<DcsCampusPoint> pts = campusMapService.getDcsPoints(mapName);
        List<MapPointResponse> out = new ArrayList<>(pts.size());
        for (int i = 0; i < pts.size(); i++) {
            DcsCampusPoint p = pts.get(i);
            out.add(new MapPointResponse(i, p.name(), p.address()));
        }
        return out;
    }

    public record MapPointResponse(int id, String name, String address) {}
}
