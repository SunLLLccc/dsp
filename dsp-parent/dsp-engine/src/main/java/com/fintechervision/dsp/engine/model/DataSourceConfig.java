package com.fintechervision.dsp.engine.model;
import lombok.Data;
@Data
public class DataSourceConfig {
    private String name;
    private String type;
    private String url;
    private String username;
    private String password;
    private String extraConfig;
}
