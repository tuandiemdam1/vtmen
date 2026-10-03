package com.vtmen.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vtmen.dcs")
public class DcsApiProperties {

    private String locationsUrl = "http://203.145.47.216:8081/task/interface/getParkPointPage";

    // Request body field for POST locations.
    private String mapName = "Trường đại học";

    // When true, run DCS location fetch and order rewrite once after the app starts.
    private boolean syncOrdersOnStartup = false;

    // DCS login credentials for auto-token (GET /sys/singleLogin).
    private String username = "";
    private String password = "";

    public String getLocationsUrl() {
        return locationsUrl;
    }

    public void setLocationsUrl(String locationsUrl) {
        this.locationsUrl = locationsUrl;
    }

    public String getMapName() {
        return mapName;
    }

    public void setMapName(String mapName) {
        this.mapName = mapName;
    }

    public boolean isSyncOrdersOnStartup() {
        return syncOrdersOnStartup;
    }

    public void setSyncOrdersOnStartup(boolean syncOrdersOnStartup) {
        this.syncOrdersOnStartup = syncOrdersOnStartup;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
