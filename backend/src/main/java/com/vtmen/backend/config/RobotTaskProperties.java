package com.vtmen.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vtmen.robot")
public class RobotTaskProperties {
    // Full URL for DCS dispatch (VtMen).
    private String dispatchUrl = "http://223.130.11.193:10101/api/task/interface/createTaskAndBegin";

    // Full URL for DCS open door control.
    private String doorControlUrl = "http://223.130.11.193:10101/task/interface/cabinetMissionOpen";

    // Used when request does not specify robot_id (capacityResourceId).
    private String defaultRobotId = "1919587605079662593";

    private String defaultCapacityResourceName = "B1B-A-277";

    private String defaultSiteId = "1919572875325743104";

    // Default destination.name in the robot payload.
    private String defaultDestinationName = "Đại học Thủy Lợi";

    public String getDispatchUrl() {
        return dispatchUrl;
    }

    public void setDispatchUrl(String dispatchUrl) {
        this.dispatchUrl = dispatchUrl;
    }

    public String getDoorControlUrl() {
        return doorControlUrl;
    }

    public void setDoorControlUrl(String doorControlUrl) {
        this.doorControlUrl = doorControlUrl;
    }

    public String getDefaultRobotId() {
        return defaultRobotId;
    }

    public void setDefaultRobotId(String defaultRobotId) {
        this.defaultRobotId = defaultRobotId;
    }

    public String getDefaultCapacityResourceName() {
        return defaultCapacityResourceName;
    }

    public void setDefaultCapacityResourceName(String defaultCapacityResourceName) {
        this.defaultCapacityResourceName = defaultCapacityResourceName;
    }

    public String getDefaultSiteId() {
        return defaultSiteId;
    }

    public void setDefaultSiteId(String defaultSiteId) {
        this.defaultSiteId = defaultSiteId;
    }

    public String getDefaultDestinationName() {
        return defaultDestinationName;
    }

    public void setDefaultDestinationName(String defaultDestinationName) {
        this.defaultDestinationName = defaultDestinationName;
    }
}
