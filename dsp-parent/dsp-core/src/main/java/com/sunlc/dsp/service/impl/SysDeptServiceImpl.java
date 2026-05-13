package com.sunlc.dsp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunlc.dsp.entity.SysDept;
import com.sunlc.dsp.mapper.SysDeptMapper;
import com.sunlc.dsp.service.SysDeptService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SysDeptServiceImpl extends ServiceImpl<SysDeptMapper, SysDept>
        implements SysDeptService {

    @Override
    public List<SysDept> getDeptTree() {
        return list(new LambdaQueryWrapper<SysDept>()
                .orderByAsc(SysDept::getName));
    }

    @Override
    public void createDept(SysDept dept) {
        save(dept);
    }

    @Override
    public void updateDept(SysDept dept) {
        updateById(dept);
    }

    @Override
    public void deleteDept(Long deptId) {
        removeById(deptId);
    }
}
