package com.sunlc.dsp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunlc.dsp.entity.SysDept;
import com.sunlc.dsp.mapper.SysDeptMapper;
import com.sunlc.dsp.service.SysDeptService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SysDeptServiceImpl extends ServiceImpl<SysDeptMapper, SysDept>
        implements SysDeptService {

    @Override
    public List<SysDept> getDeptTree() {
        List<SysDept> all = list(new LambdaQueryWrapper<SysDept>()
                .orderByAsc(SysDept::getSortOrder));
        return buildTree(all, 0L);
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

    private List<SysDept> buildTree(List<SysDept> all, Long parentId) {
        Map<Long, List<SysDept>> grouped = all.stream()
                .collect(Collectors.groupingBy(SysDept::getParentId));
        List<SysDept> roots = grouped.getOrDefault(parentId, new ArrayList<>());
        return roots;
    }
}
