package com.sunlc.dsp.adminservice.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunlc.dsp.adminservice.annotation.RequireRole;
import com.sunlc.dsp.common.model.ApiResponse;
import com.sunlc.dsp.entity.SysUser;
import com.sunlc.dsp.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/dsp/admin/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;

    @GetMapping("/list")
    public ApiResponse<Page<SysUser>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String realName) {

        Page<SysUser> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (username != null && !username.isEmpty()) {
            wrapper.like(SysUser::getUsername, username);
        }
        if (realName != null && !realName.isEmpty()) {
            wrapper.like(SysUser::getRealName, realName);
        }
        wrapper.orderByDesc(SysUser::getCreatedTime);

        Page<SysUser> result = sysUserService.page(page, wrapper);
        // 清除密码字段
        result.getRecords().forEach(u -> u.setPassword(null));
        // 批量填充角色
        sysUserService.fillUserRoles(result.getRecords());
        return ApiResponse.success("USER", "LIST", result);
    }

    @GetMapping("/{id}")
    public ApiResponse<SysUser> detail(@PathVariable Long id) {
        SysUser user = sysUserService.getById(id);
        if (user != null) {
            user.setPassword(null);
            sysUserService.fillUserRoles(java.util.Collections.singletonList(user));
        }
        return ApiResponse.success("USER", "DETAIL", user);
    }

    @PostMapping
    @RequireRole({"ADMIN"})
    public ApiResponse<Void> create(@RequestBody SysUser user) {
        sysUserService.createUser(user);
        return ApiResponse.success("USER", "CREATE", null);
    }

    @PutMapping("/{id}")
    @RequireRole({"ADMIN"})
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody SysUser user) {
        user.setId(id);
        user.setPassword(null);
        sysUserService.updateById(user);
        return ApiResponse.success("USER", "UPDATE", null);
    }

    @PutMapping("/{id}/password")
    @RequireRole({"ADMIN"})
    public ApiResponse<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String newPassword = body.get("password");
        if (newPassword == null || newPassword.isEmpty()) {
            return ApiResponse.error("USER", "RESET_PWD", "4001", "密码不能为空");
        }
        sysUserService.resetPassword(id, newPassword);
        return ApiResponse.success("USER", "RESET_PWD", null);
    }

    @PutMapping("/{id}/status")
    @RequireRole({"ADMIN"})
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setStatus(body.get("status"));
        sysUserService.updateById(user);
        return ApiResponse.success("USER", "UPDATE_STATUS", null);
    }

    @DeleteMapping("/{id}")
    @RequireRole({"ADMIN"})
    public ApiResponse<Void> delete(@PathVariable Long id) {
        sysUserService.removeById(id);
        return ApiResponse.success("USER", "DELETE", null);
    }

    @PostMapping("/{id}/roles")
    @RequireRole({"ADMIN"})
    public ApiResponse<Void> assignRoles(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        List<Long> roleIds = body.get("roleIds");
        if (roleIds == null) {
            return ApiResponse.error("USER", "ASSIGN_ROLES", "4001", "角色列表不能为空");
        }
        sysUserService.assignRoles(id, roleIds);
        return ApiResponse.success("USER", "ASSIGN_ROLES", null);
    }
}
