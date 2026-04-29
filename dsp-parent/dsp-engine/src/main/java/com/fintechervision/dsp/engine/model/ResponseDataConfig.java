package com.fintechervision.dsp.engine.model;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;
@Data
public class ResponseDataConfig {
    private String resultMap;
    private List<ResponseFieldConfig> fields = new ArrayList<>();
}
