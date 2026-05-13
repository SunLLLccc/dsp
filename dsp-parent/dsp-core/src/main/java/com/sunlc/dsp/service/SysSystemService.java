package com.sunlc.dsp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sunlc.dsp.entity.SysSystem;

import java.util.List;

public interface SysSystemService extends IService<SysSystem> {
    List<SysSystem> listByDeptId(Long deptId);
    SysSystem getByCode(String code);
}
