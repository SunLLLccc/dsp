package com.sunlc.dsp.engine.model;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;
@Data
public class InterfaceConfig {
    private String transno;
    private String name;
    private String description;
    private RequestDataConfig requestData;
    private List<DataSourceConfig> dataSources = new ArrayList<>();
    private List<QueryConfig> queries = new ArrayList<>();
    private List<ResultMapConfig> resultMaps = new ArrayList<>();
    private ResponseDataConfig responseData;
}
