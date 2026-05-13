package com.sunlc.dsp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sunlc.dsp.entity.SysDept;

import java.util.List;

public interface SysDeptService extends IService<SysDept> {
    List<SysDept> getDeptTree();
    void createDept(SysDept dept);
    void updateDept(SysDept dept);
    void deleteDept(Long deptId);
}
