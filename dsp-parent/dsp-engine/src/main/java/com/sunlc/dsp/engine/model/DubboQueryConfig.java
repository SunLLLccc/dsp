package com.sunlc.dsp.engine.model;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;
@Data
public class DubboQueryConfig {
    private String service;
    private String method;
    private String version = "1.0.0";
    private String group;
    private int timeout = 3000;
    private List<DubboParam> params = new ArrayList<>();
    @Data
    public static class DubboParam {
        private String type;
        private String value;
    }
}
