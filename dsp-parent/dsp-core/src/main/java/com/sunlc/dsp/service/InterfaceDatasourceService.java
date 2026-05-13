package com.sunlc.dsp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sunlc.dsp.entity.InterfaceDatasource;

import java.util.List;

/**
 * 接口-数据源关联服务
 */
public interface InterfaceDatasourceService extends IService<InterfaceDatasource> {

    /**
     * 查询接口关联的数据源名称列表
     */
    List<String> listDsNamesByTransno(String transno);

    /**
     * 查询接口关联的数据源配置列表
     */
    List<InterfaceDatasource> listByTransno(String transno);

    /**
     * 为接口绑定数据源（全量替换）
     */
    void bindDatasources(String transno, List<String> dsNames);

    /**
     * 为接口添加单个数据源关联
     */
    void addDatasource(String transno, String dsName);

    /**
     * 移除接口的单个数据源关联
     */
    void removeDatasource(String transno, String dsName);
}
