package com.sunlc.dsp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunlc.dsp.common.util.PasswordEncryptor;
import com.sunlc.dsp.entity.DatasourceConfig;
import com.sunlc.dsp.enums.CommonStatus;
import com.sunlc.dsp.mapper.DatasourceConfigMapper;
import com.sunlc.dsp.service.DatasourceManagerService;
import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.creator.DataSourceProperty;
import com.baomidou.dynamic.datasource.creator.DefaultDataSourceCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourceManagerServiceImpl extends ServiceImpl<DatasourceConfigMapper, DatasourceConfig>
        implements DatasourceManagerService {

    private final DynamicRoutingDataSource dynamicRoutingDataSource;
    private final DefaultDataSourceCreator defaultDataSourceCreator;
    private final PasswordEncryptor passwordEncryptor;

    /** 允许测试连接的 JDBC 数据源类型及其对应 URL 前缀 */
    private static final Map<String, String> ALLOWED_JDBC_TYPES = new LinkedHashMap<>();
    static {
        ALLOWED_JDBC_TYPES.put("MYSQL", "jdbc:mysql:");
        ALLOWED_JDBC_TYPES.put("DORIS", "jdbc:mysql:");
        ALLOWED_JDBC_TYPES.put("ORACLE", "jdbc:oracle:");
        ALLOWED_JDBC_TYPES.put("POSTGRESQL", "jdbc:postgresql:");
    }

    @Override
    public DatasourceConfig getByDsName(String dsName) {
        LambdaQueryWrapper<DatasourceConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasourceConfig::getDsName, dsName)
               .eq(DatasourceConfig::getStatus, CommonStatus.ENABLED.getCode());
        return getOne(wrapper);
    }

    @Override
    public void loadAndRegisterAll() {
        List<DatasourceConfig> configs = listEnabled();
        for (DatasourceConfig config : configs) {
            try {
                registerDatasource(config);
            } catch (Exception e) {
                log.error("数据源注册失败: dsName={}, error={}", config.getDsName(), e.getMessage());
            }
        }
        log.info("数据源加载完成，共注册 {} 个", configs.size());
    }

    @Override
    public void registerDatasource(DatasourceConfig config) {
        String dsType = config.getDsType().toUpperCase();
        if ("HTTP".equals(dsType) || "DUBBO".equals(dsType)) {
            log.info("非JDBC数据源[{}]，跳过Dynamic-DS注册", config.getDsName());
            return;
        }

        Map<String, DataSource> currentDataSources = dynamicRoutingDataSource.getDataSources();
        if (currentDataSources.containsKey(config.getDsName())) {
            dynamicRoutingDataSource.removeDataSource(config.getDsName());
        }

        DataSourceProperty property = new DataSourceProperty();
        property.setUrl(config.getJdbcUrl());
        property.setUsername(config.getUsername());
        property.setPassword(passwordEncryptor.decrypt(config.getPassword()));

        switch (dsType) {
            case "MYSQL": case "DORIS":
                property.setDriverClassName("com.mysql.cj.jdbc.Driver"); break;
            case "ORACLE":
                property.setDriverClassName("oracle.jdbc.OracleDriver"); break;
            case "POSTGRESQL":
                property.setDriverClassName("org.postgresql.Driver"); break;
            default:
                property.setDriverClassName("com.mysql.cj.jdbc.Driver");
        }

        property.setType(com.alibaba.druid.pool.DruidDataSource.class);
        DataSource dataSource = defaultDataSourceCreator.createDataSource(property);
        dynamicRoutingDataSource.addDataSource(config.getDsName(), dataSource);
        log.info("数据源注册成功: dsName={}, type={}, url={}", config.getDsName(), dsType, config.getJdbcUrl());
    }

    @Override
    public void removeDatasource(String dsName) {
        dynamicRoutingDataSource.removeDataSource(dsName);
        log.info("数据源注销成功: dsName={}", dsName);
    }

    @Override
    public String testConnection(DatasourceConfig config) {
        // 1. 参数校验
        if (config.getDsType() == null || config.getDsType().isEmpty()) {
            throw new com.sunlc.dsp.common.exception.BusinessException(
                    com.sunlc.dsp.common.enums.ErrorCode.BAD_REQUEST, "缺少数据源类型(dsType)");
        }
        if (config.getJdbcUrl() == null || config.getJdbcUrl().isEmpty()) {
            throw new com.sunlc.dsp.common.exception.BusinessException(
                    com.sunlc.dsp.common.enums.ErrorCode.BAD_REQUEST, "缺少JDBC连接地址(jdbcUrl)");
        }

        String dsType = config.getDsType().toUpperCase();

        // 2. 校验数据源类型白名单
        if (!ALLOWED_JDBC_TYPES.containsKey(dsType)) {
            String allowed = String.join(", ", ALLOWED_JDBC_TYPES.keySet());
            log.warn("不支持的数据源类型: dsType={}, allowed={}", dsType, allowed);
            throw new com.sunlc.dsp.common.exception.BusinessException(
                    com.sunlc.dsp.common.enums.ErrorCode.BAD_REQUEST,
                    "不支持的数据源类型，允许的类型: " + allowed);
        }

        // 3. 校验 JDBC URL 前缀
        String expectedPrefix = ALLOWED_JDBC_TYPES.get(dsType);
        if (!config.getJdbcUrl().toLowerCase().startsWith(expectedPrefix)) {
            log.warn("JDBC URL与数据源类型不匹配: dsType={}, urlPrefix={}", dsType, expectedPrefix);
            throw new com.sunlc.dsp.common.exception.BusinessException(
                    com.sunlc.dsp.common.enums.ErrorCode.BAD_REQUEST,
                    "JDBC URL格式与数据源类型(" + dsType + ")不匹配");
        }

        // 4. 尝试连接（不通过 registerDatasource，避免成功日志输出完整 jdbcUrl）
        DataSourceProperty property = new DataSourceProperty();
        property.setUrl(config.getJdbcUrl());
        property.setUsername(config.getUsername());
        String rawPassword = config.getPassword();
        if (rawPassword != null && rawPassword.startsWith("ENC(")) {
            rawPassword = passwordEncryptor.decrypt(rawPassword);
        }
        property.setPassword(rawPassword);
        switch (dsType) {
            case "MYSQL": case "DORIS":
                property.setDriverClassName("com.mysql.cj.jdbc.Driver"); break;
            case "ORACLE":
                property.setDriverClassName("oracle.jdbc.OracleDriver"); break;
            case "POSTGRESQL":
                property.setDriverClassName("org.postgresql.Driver"); break;
            default:
                property.setDriverClassName("com.mysql.cj.jdbc.Driver");
        }
        property.setType(com.alibaba.druid.pool.DruidDataSource.class);

        String dsName = config.getDsName();
        DataSource dataSource = null;
        try {
            dataSource = defaultDataSourceCreator.createDataSource(property);
            // 测试获取连接以验证可用性
            try (java.sql.Connection conn = dataSource.getConnection()) {
                // 连接成功即关闭
            }
            log.info("数据源测试连接成功: dsName={}, dsType={}", dsName, dsType);
            return "连接成功";
        } catch (Exception e) {
            // 脱敏日志：只记录 dsName、dsType、异常类名和错误分类，不打印原始 message 和堆栈
            String errorCategory = classifyConnectionError(e);
            log.warn("数据源测试连接失败: dsName={}, dsType={}, errorCategory={}", dsName, dsType, errorCategory);
            return "连接失败，请检查数据库地址、端口、用户名和密码是否正确";
        } finally {
            // 关闭/销毁临时 DataSource，释放连接池资源
            if (dataSource != null) {
                try {
                    if (dataSource instanceof com.alibaba.druid.pool.DruidDataSource) {
                        ((com.alibaba.druid.pool.DruidDataSource) dataSource).close();
                    } else if (dataSource instanceof AutoCloseable) {
                        ((AutoCloseable) dataSource).close();
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public List<DatasourceConfig> listAll() { return list(); }

    private List<DatasourceConfig> listEnabled() {
        LambdaQueryWrapper<DatasourceConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasourceConfig::getStatus, CommonStatus.ENABLED.getCode());
        return list(wrapper);
    }

    /**
     * 对连接异常进行脱敏分类，只返回大致错误类别，不暴露连接细节
     */
    private String classifyConnectionError(Exception e) {
        String exClass = e.getClass().getSimpleName();
        if (e instanceof java.sql.SQLException) return "SQL_ERROR(" + exClass + ")";
        if (e instanceof java.net.ConnectException) return "CONNECTION_REFUSED";
        if (e instanceof java.net.UnknownHostException) return "UNKNOWN_HOST";
        if (e instanceof java.io.IOException) return "IO_ERROR(" + exClass + ")";
        Throwable cause = e.getCause();
        if (cause != null) {
            String causeClass = cause.getClass().getSimpleName();
            if (cause instanceof java.sql.SQLException) return "SQL_ERROR(" + causeClass + ")";
            if (cause instanceof java.net.ConnectException) return "CONNECTION_REFUSED";
            if (cause instanceof java.net.UnknownHostException) return "UNKNOWN_HOST";
            if (cause instanceof java.io.IOException) return "IO_ERROR(" + causeClass + ")";
        }
        return "UNKNOWN(" + exClass + ")";
    }
}
