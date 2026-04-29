package com.fintechervision.dsp.engine.model;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;
@Data
public class ResultMapConfig {
    private String id;
    private String query;
    private List<FieldMapping> fields = new ArrayList<>();
    @Data
    public static class FieldMapping {
        private String name;
        private String column;
        private String function;
    }
}
