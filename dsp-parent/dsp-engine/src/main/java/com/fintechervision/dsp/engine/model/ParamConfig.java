package com.fintechervision.dsp.engine.model;
import lombok.Data;
@Data
public class ParamConfig {
    private String name;
    private String type;
    private boolean required;
    private String defaultValue;
    private String description;
}
