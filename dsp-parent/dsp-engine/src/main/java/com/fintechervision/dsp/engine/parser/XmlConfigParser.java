package com.fintechervision.dsp.engine.parser;

import com.fintechervision.dsp.engine.model.*;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Text;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class XmlConfigParser {

    public InterfaceConfig parse(String xmlContent) {
        try {
            Document document = DocumentHelper.parseText(xmlContent);
            Element root = document.getRootElement();
            return parseInterface(root);
        } catch (Exception e) {
            throw new RuntimeException("XML配置解析失败: " + e.getMessage(), e);
        }
    }

    private InterfaceConfig parseInterface(Element element) {
        InterfaceConfig config = new InterfaceConfig();
        config.setTransno(element.attributeValue("transno"));
        config.setName(element.attributeValue("name"));
        config.setDescription(element.attributeValue("description"));

        for (Element child : element.elements()) {
            String tagName = child.getName();
            switch (tagName) {
                case "requestData":
                    config.setRequestData(parseRequestData(child));
                    break;
                case "datasource":
                    config.getDataSources().add(parseDataSource(child));
                    break;
                case "query":
                    config.getQueries().add(parseQuery(child));
                    break;
                case "resultMap":
                    config.getResultMaps().add(parseResultMap(child));
                    break;
                case "responseData":
                    config.setResponseData(parseResponseData(child));
                    break;
                default:
                    log.warn("未知的XML标签: {}", tagName);
            }
        }
        return config;
    }

    private RequestDataConfig parseRequestData(Element element) {
        RequestDataConfig config = new RequestDataConfig();
        for (Element paramEl : element.elements("param")) {
            ParamConfig param = new ParamConfig();
            param.setName(paramEl.attributeValue("name"));
            param.setType(paramEl.attributeValue("type", "String"));
            param.setRequired("true".equals(paramEl.attributeValue("required", "false")));
            param.setDefaultValue(paramEl.attributeValue("defaultValue"));
            param.setDescription(paramEl.attributeValue("description"));
            config.getParams().add(param);
        }
        return config;
    }

    private DataSourceConfig parseDataSource(Element element) {
        DataSourceConfig config = new DataSourceConfig();
        config.setName(element.attributeValue("name"));
        config.setType(element.attributeValue("type", "MYSQL"));
        config.setUrl(element.attributeValue("url"));
        config.setUsername(element.attributeValue("username"));
        config.setPassword(element.attributeValue("password"));
        config.setExtraConfig(element.attributeValue("extraConfig"));
        return config;
    }

    private QueryConfig parseQuery(Element element) {
        QueryConfig config = new QueryConfig();
        config.setId(element.attributeValue("id"));
        config.setType(element.attributeValue("type", "mysql"));
        config.setDatasource(element.attributeValue("datasource"));
        config.setRef(element.attributeValue("ref"));

        String depends = element.attributeValue("depends");
        if (depends != null && !depends.isEmpty()) {
            for (String dep : depends.split(",")) {
                config.getDepends().add(dep.trim());
            }
        }

        parsePagination(element, config);

        String type = config.getType().toLowerCase();
        if ("mysql".equals(type) || "doris".equals(type) || "sql".equals(type)
                || "oracle".equals(type) || "postgresql".equals(type)) {
            parseSqlContent(element, config);
        } else if ("http".equals(type)) {
            parseHttpQuery(element, config);
        } else if ("dubbo".equals(type)) {
            parseDubboQuery(element, config);
        } else if ("mongo".equals(type)) {
            parseMongoQuery(element, config);
        } else {
            log.warn("暂不支持的查询类型: {}，按SQL处理", type);
            parseSqlContent(element, config);
        }

        return config;
    }

    private void parseSqlContent(Element element, QueryConfig config) {
        StringBuilder sqlBuilder = new StringBuilder();
        for (Object node : element.content()) {
            if (node instanceof Element) {
                Element child = (Element) node;
                String tagName = child.getName();
                if ("if".equals(tagName)) {
                    config.getDynamicSqls().add(parseIfTag(child));
                } else if ("foreach".equals(tagName)) {
                    config.getDynamicSqls().add(parseForeachTag(child));
                }
            } else if (node instanceof Text) {
                String text = ((Text) node).getText().trim();
                if (!text.isEmpty()) {
                    sqlBuilder.append(" ").append(text);
                }
            }
        }
        config.setSql(sqlBuilder.toString().trim());
    }

    private void parseHttpQuery(Element element, QueryConfig config) {
        Element httpEl = element.element("http");
        if (httpEl == null) {
            throw new RuntimeException("HTTP查询缺少<http>配置标签");
        }
        HttpQueryConfig httpConfig = new HttpQueryConfig();
        httpConfig.setUrl(httpEl.attributeValue("url"));
        httpConfig.setMethod(httpEl.attributeValue("method", "GET"));
        httpConfig.setHeaders(httpEl.attributeValue("headers"));
        httpConfig.setBody(httpEl.attributeValue("body"));
        httpConfig.setResponsePath(httpEl.attributeValue("responsePath"));
        config.setHttpConfig(httpConfig);
    }

    private void parseDubboQuery(Element element, QueryConfig config) {
        Element dubboEl = element.element("dubbo");
        if (dubboEl == null) {
            throw new RuntimeException("Dubbo查询缺少<dubbo>配置标签");
        }
        DubboQueryConfig dubboConfig = new DubboQueryConfig();
        dubboConfig.setService(dubboEl.attributeValue("service"));
        dubboConfig.setMethod(dubboEl.attributeValue("method"));
        dubboConfig.setVersion(dubboEl.attributeValue("version", "1.0.0"));
        dubboConfig.setGroup(dubboEl.attributeValue("group"));
        dubboConfig.setTimeout(Integer.parseInt(dubboEl.attributeValue("timeout", "3000")));

        for (Element paramEl : dubboEl.elements("param")) {
            DubboQueryConfig.DubboParam param = new DubboQueryConfig.DubboParam();
            param.setType(paramEl.attributeValue("type", "String"));
            param.setValue(paramEl.attributeValue("value"));
            dubboConfig.getParams().add(param);
        }

        config.setDubboConfig(dubboConfig);
    }

    private void parseMongoQuery(Element element, QueryConfig config) {
        Element mongoEl = element.element("mongo");
        if (mongoEl == null) {
            throw new RuntimeException("MongoDB查询缺少<mongo>配置标签");
        }
        MongoQueryConfig mongoConfig = new MongoQueryConfig();
        mongoConfig.setCollection(mongoEl.attributeValue("collection"));

        Element filterEl = mongoEl.element("filter");
        if (filterEl != null) mongoConfig.setFilter(filterEl.getTextTrim());

        Element projectionEl = mongoEl.element("projection");
        if (projectionEl != null) mongoConfig.setProjection(projectionEl.getTextTrim());

        Element sortEl = mongoEl.element("sort");
        if (sortEl != null) mongoConfig.setSort(sortEl.getTextTrim());

        Element limitEl = mongoEl.element("limit");
        if (limitEl != null) mongoConfig.setLimit(Integer.parseInt(limitEl.getTextTrim()));

        Element skipEl = mongoEl.element("skip");
        if (skipEl != null) mongoConfig.setSkip(Integer.parseInt(skipEl.getTextTrim()));

        config.setMongoConfig(mongoConfig);
    }

    private void parsePagination(Element element, QueryConfig config) {
        String pagination = element.attributeValue("pagination");
        if (pagination == null || pagination.isEmpty()) {
            return;
        }

        PaginationConfig pc = new PaginationConfig();

        switch (pagination.toLowerCase()) {
            case "cursor":
                pc.setMode(PaginationConfig.PaginationMode.CURSOR);
                break;
            case "optimized":
                pc.setMode(PaginationConfig.PaginationMode.OPTIMIZED);
                break;
            default:
                log.warn("未知的分页模式: {}，跳过分页配置", pagination);
                return;
        }

        pc.setOrderBy(element.attributeValue("order-by", "id"));
        pc.setPageSizeParam(element.attributeValue("page-size-param", "pageSize"));
        pc.setLastIdParam(element.attributeValue("last-id-param", "lastId"));
        pc.setPageNumParam(element.attributeValue("page-num-param", "pageNum"));

        String defaultPageSize = element.attributeValue("default-page-size");
        if (defaultPageSize != null && !defaultPageSize.isEmpty()) {
            pc.setDefaultPageSize(Integer.parseInt(defaultPageSize));
        }

        String maxPageSize = element.attributeValue("max-page-size");
        if (maxPageSize != null && !maxPageSize.isEmpty()) {
            pc.setMaxPageSize(Integer.parseInt(maxPageSize));
        }

        config.setPaginationConfig(pc);
    }

    private DynamicSqlConfig parseIfTag(Element element) {
        DynamicSqlConfig config = new DynamicSqlConfig();
        config.setType(DynamicSqlConfig.DynamicType.IF);
        config.setTest(element.attributeValue("test"));
        config.setSql(element.getTextTrim());
        return config;
    }

    private DynamicSqlConfig parseForeachTag(Element element) {
        DynamicSqlConfig config = new DynamicSqlConfig();
        config.setType(DynamicSqlConfig.DynamicType.FOREACH);
        config.setCollection(element.attributeValue("collection"));
        config.setItem(element.attributeValue("item", "item"));
        config.setSeparator(element.attributeValue("separator", ","));
        config.setOpen(element.attributeValue("open", ""));
        config.setClose(element.attributeValue("close", ""));
        config.setForeachSql(element.getTextTrim());
        return config;
    }

    private ResultMapConfig parseResultMap(Element element) {
        ResultMapConfig config = new ResultMapConfig();
        config.setId(element.attributeValue("id"));
        config.setQuery(element.attributeValue("query"));

        for (Element fieldEl : element.elements("field")) {
            ResultMapConfig.FieldMapping field = new ResultMapConfig.FieldMapping();
            field.setName(fieldEl.attributeValue("name"));
            field.setColumn(fieldEl.attributeValue("column"));
            field.setFunction(fieldEl.attributeValue("function"));
            config.getFields().add(field);
        }
        return config;
    }

    private ResponseDataConfig parseResponseData(Element element) {
        ResponseDataConfig config = new ResponseDataConfig();
        config.setResultMap(element.attributeValue("resultMap"));

        for (Element fieldEl : element.elements("field")) {
            ResponseFieldConfig field = new ResponseFieldConfig();
            field.setName(fieldEl.attributeValue("name"));
            field.setMapTo(fieldEl.attributeValue("mapTo"));
            field.setFunction(fieldEl.attributeValue("function"));
            config.getFields().add(field);
        }
        return config;
    }
}
