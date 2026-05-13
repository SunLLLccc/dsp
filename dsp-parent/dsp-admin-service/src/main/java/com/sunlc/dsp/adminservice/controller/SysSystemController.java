package com.sunlc.dsp.adminservice.controller;

import com.sunlc.dsp.adminservice.annotation.RequireRole;
import com.sunlc.dsp.common.model.ApiResponse;
import com.sunlc.dsp.entity.SysSystem;
import com.sunlc.dsp.service.SysSystemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/dsp/admin/system")
@RequiredArgsConstructor
public class SysSystemController {

    private final SysSystemService sysSystemService;

    @GetMapping("/list")
    public ApiResponse<List<SysSystem>> list(@RequestParam(required = false) Long deptId) {
        if (deptId != null) {
            return ApiResponse.success("SYSTEM", "LIST", sysSystemService.listByDeptId(deptId));
        }
        return ApiResponse.success("SYSTEM", "LIST", sysSystemService.list());
    }

    @PostMapping
    @RequireRole({"ADMIN"})
    public ApiResponse<Void> create(@RequestBody SysSystem system) {
        sysSystemService.save(system);
        return ApiResponse.success("SYSTEM", "CREATE", null);
    }

    @PutMapping("/{id}")
    @RequireRole({"ADMIN"})
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody SysSystem system) {
        system.setId(id);
        sysSystemService.updateById(system);
        return ApiResponse.success("SYSTEM", "UPDATE", null);
    }

    @DeleteMapping("/{id}")
    @RequireRole({"ADMIN"})
    public ApiResponse<Void> delete(@PathVariable Long id) {
        sysSystemService.removeById(id);
        return ApiResponse.success("SYSTEM", "DELETE", null);
    }
}
