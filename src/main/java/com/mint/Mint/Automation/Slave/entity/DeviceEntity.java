package com.mint.Mint.Automation.Slave.entity;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "devices")
@Data
@Builder
public class DeviceEntity {

    @Id
    private String id;
    private String name;
    private String os;
    private String osVersion;
    private String brand;
    private Integer width;
    private Integer height;
    @Builder.Default
    private String deviceType = "mobile";
    @Builder.Default
    private Boolean available = true;
    private String device_img;
    @Builder.Default
    private String wda_port = null;
    @Builder.Default
    private String mjpeg_port = null;
    @Builder.Default
    private String session_id = "";
    private String stream_url;
    private String master_ip;
    private String master_name;
    @Builder.Default
    private LocalDateTime connection_time_date = LocalDateTime.now();
    @Builder.Default
    private String environment = "Public";
    @Builder.Default
    private String session_owner = "";
    private LocalDateTime startedAt;
    private String description;
    private String service_id;
    private String appium_session_id;
}

