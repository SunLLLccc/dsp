package com.fintechervision.dsp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fintechervision.dsp.common.enums.ErrorCode;
import com.fintechervision.dsp.common.exception.BusinessException;
import com.fintechervision.dsp.entity.InterfaceDatasource;
import com.fintechervision.dsp.mapper.InterfaceDatasourceMapper;
import com.fintechervision.dsp.service.InterfaceDatasourceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 接口-数据源关联服务实现
 */
@Slf4j
@Service
public class InterfaceDatasourceServiceImpl extends ServiceImpl<InterfaceDatasourceMapper, InterfaceDatasource>
        implements InterfaceDatasourceService {

    @Override
    public List<String> listDsNamesByTransno(String transno) {
        LambdaQueryWrapper<InterfaceDatasource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterfaceDatasource::getTransno, transno);
        return list(wrapper).stream().map(InterfaceDatasource::getDsName).collect(Collectors.toList());
    }

    @Override
    public List<InterfaceDatasource> listByTransno(String transno) {
        LambdaQueryWrapper<InterfaceDatasource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterfaceDatasource::getTransno, transno);
        return list(wrapper);
    }

    @Override
    @Transactional
    public void bindDatasources(String transno, List<String> dsNames) {
        // 先删除旧关联
        LambdaQueryWrapper<InterfaceDatasource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterfaceDatasource::getTransno, transno);
        remove(wrapper);
        // 再批量插入新关联
        if (dsNames != null && !dsNames.isEmpty()) {
            List<InterfaceDatasource> list = dsNames.stream().map(dsName -> {
                InterfaceDatasource rel = new InterfaceDatasource();
                rel.setTransno(transno);
                rel.setDsName(dsName);
                rel.setCreatedTime(LocalDateTime.now());
                return rel;
            }).collect(Collectors.toList());
            saveBatch(list);
        }
        log.info("接口数据源绑定: transno={}, dsNames={}", transno, dsNames);
    }

    @Override
    public void addDatasource(String transno, String dsName) {
        LambdaQueryWrapper<InterfaceDatasource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterfaceDatasource::getTransno, transno).eq(InterfaceDatasource::getDsName, dsName);
        if (count(wrapper) > 0) {
            throw new BusinessException(ErrorCode.DATASOURCE_BIND_DUPLICATE,
                    "关联已存在: transno=" + transno + ", dsName=" + dsName);
        }
        InterfaceDatasource rel = new InterfaceDatasource();
        rel.setTransno(transno);
        rel.setDsName(dsName);
        rel.setCreatedTime(LocalDateTime.now());
        save(rel);
        log.info("接口数据源关联添加: transno={}, dsName={}", transno, dsName);
    }

    @Override
    public void removeDatasource(String transno, String dsName) {
        LambdaQueryWrapper<InterfaceDatasource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterfaceDatasource::getTransno, transno).eq(InterfaceDatasource::getDsName, dsName);
        remove(wrapper);
        log.info("接口数据源关联移除: transno={}, dsName={}", transno, dsName);
    }
}
