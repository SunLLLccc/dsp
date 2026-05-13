package com.sunlc.dsp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunlc.dsp.common.enums.ErrorCode;
import com.sunlc.dsp.common.exception.BusinessException;
import com.sunlc.dsp.entity.SysRole;
import com.sunlc.dsp.entity.SysUser;
import com.sunlc.dsp.entity.SysUserRole;
import com.sunlc.dsp.mapper.SysRoleMapper;
import com.sunlc.dsp.mapper.SysUserMapper;
import com.sunlc.dsp.mapper.SysUserRoleMapper;
import com.sunlc.dsp.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser>
        implements SysUserService {

    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public SysUser login(String username, String password) {
        SysUser user = getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (user == null) {
            return null;
        }
        if (user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "账号已被禁用");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            return null;
        }
        return user;
    }

    @Override
    public List<String> getRoleCodes(Long userId) {
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (userRoles.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        List<SysRole> roles = sysRoleMapper.selectBatchIds(roleIds);
        return roles.stream().map(SysRole::getRoleCode).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
        for (Long roleId : roleIds) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            sysUserRoleMapper.insert(ur);
        }
    }

    @Override
    public void createUser(SysUser user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        save(user);
    }

    @Override
    public void resetPassword(Long userId, String newPassword) {
        SysUser user = getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "用户不存在");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        updateById(user);
    }

    @Override
    public void fillUserRoles(List<SysUser> users) {
        if (users == null || users.isEmpty()) return;

        List<Long> userIds = users.stream().map(SysUser::getId).collect(Collectors.toList());
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getUserId, userIds));

        if (userRoles.isEmpty()) return;

        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).distinct().collect(Collectors.toList());
        List<SysRole> roles = sysRoleMapper.selectBatchIds(roleIds);
        java.util.Map<Long, SysRole> roleMap = roles.stream().collect(Collectors.toMap(SysRole::getId, r -> r));

        java.util.Map<Long, java.util.List<SysRole>> userRoleMap = new java.util.HashMap<>();
        for (SysUserRole ur : userRoles) {
            userRoleMap.computeIfAbsent(ur.getUserId(), k -> new java.util.ArrayList<>())
                    .add(roleMap.get(ur.getRoleId()));
        }

        for (SysUser user : users) {
            java.util.List<SysRole> userRolesList = userRoleMap.getOrDefault(user.getId(), java.util.Collections.emptyList());
            user.setRoleNames(userRolesList.stream().map(SysRole::getRoleName).collect(Collectors.toList()));
            user.setRoleIds(userRolesList.stream().map(SysRole::getId).collect(Collectors.toList()));
        }
    }
}
