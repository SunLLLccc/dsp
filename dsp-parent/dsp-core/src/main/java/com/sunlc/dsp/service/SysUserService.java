package com.sunlc.dsp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sunlc.dsp.entity.SysUser;

import java.util.List;

public interface SysUserService extends IService<SysUser> {
    SysUser login(String username, String password);
    List<String> getRoleCodes(Long userId);
    void assignRoles(Long userId, List<Long> roleIds);
    void createUser(SysUser user);
    void resetPassword(Long userId, String newPassword);
    void fillUserRoles(List<SysUser> users);
    SysUser getProfile(String username);
    void updateProfile(String username, String realName);
    void changePassword(String username, String oldPassword, String newPassword);
}
