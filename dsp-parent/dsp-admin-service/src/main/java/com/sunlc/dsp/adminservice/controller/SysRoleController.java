package com.sunlc.dsp.adminservice.controller;

import com.sunlc.dsp.common.model.ApiResponse;
import com.sunlc.dsp.entity.SysRole;
import com.sunlc.dsp.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/dsp/admin/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService sysRoleService;

    @GetMapping("/list")
    public ApiResponse<List<SysRole>> list() {
        return ApiResponse.success("ROLE", "LIST", sysRoleService.list());
    }
}
