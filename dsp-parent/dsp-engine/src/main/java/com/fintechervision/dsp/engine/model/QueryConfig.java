package com.fintechervision.dsp.engine.model;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;
@Data
public class QueryConfig {
    private String id;
    private String type = "mysql";
    private String datasource;
    private String ref;
    private String sql;
    private List<DynamicSqlConfig> dynamicSqls = new ArrayList<>();
    private HttpQueryConfig httpConfig;
    private DubboQueryConfig dubboConfig;
    private MongoQueryConfig mongoConfig;
    private PaginationConfig paginationConfig;
    private List<String> depends = new ArrayList<>();
}
