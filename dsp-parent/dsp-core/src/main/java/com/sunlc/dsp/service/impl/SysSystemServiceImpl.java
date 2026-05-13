package com.sunlc.dsp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunlc.dsp.entity.SysSystem;
import com.sunlc.dsp.mapper.SysSystemMapper;
import com.sunlc.dsp.service.SysSystemService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SysSystemServiceImpl extends ServiceImpl<SysSystemMapper, SysSystem>
        implements SysSystemService {

    @Override
    public List<SysSystem> listByDeptId(Long deptId) {
        return list(new LambdaQueryWrapper<SysSystem>()
                .eq(deptId != null, SysSystem::getDeptId, deptId)
                .eq(SysSystem::getStatus, 1)
                .orderByAsc(SysSystem::getId));
    }
}
