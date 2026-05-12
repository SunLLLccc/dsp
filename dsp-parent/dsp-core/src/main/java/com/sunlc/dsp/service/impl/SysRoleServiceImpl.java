package com.sunlc.dsp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunlc.dsp.entity.SysRole;
import com.sunlc.dsp.mapper.SysRoleMapper;
import com.sunlc.dsp.service.SysRoleService;
import org.springframework.stereotype.Service;

@Service
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole>
        implements SysRoleService {
}
