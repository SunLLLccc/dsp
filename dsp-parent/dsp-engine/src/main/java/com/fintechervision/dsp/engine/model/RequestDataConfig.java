package com.fintechervision.dsp.engine.model;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;
@Data
public class RequestDataConfig {
    private List<ParamConfig> params = new ArrayList<>();
}
