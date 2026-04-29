package com.fintechervision.dsp.engine.model;
import lombok.Data;
@Data
public class HttpQueryConfig {
    private String url;
    private String method = "GET";
    private String headers;
    private String body;
    private String responsePath;
}
