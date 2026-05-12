package com.sunlc.dsp.adminservice.controller;

import com.sunlc.dsp.adminservice.annotation.RequireRole;
import com.sunlc.dsp.common.model.ApiResponse;
import com.sunlc.dsp.entity.SysDept;
import com.sunlc.dsp.service.SysDeptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/dsp/admin/dept")
@RequiredArgsConstructor
public class SysDeptController {

    private final SysDeptService sysDeptService;

    @GetMapping("/tree")
    public ApiResponse<List<SysDept>> tree() {
        return ApiResponse.success("DEPT", "TREE", sysDeptService.getDeptTree());
    }

    @PostMapping
    @RequireRole({"ADMIN"})
    public ApiResponse<Void> create(@RequestBody SysDept dept) {
        sysDeptService.createDept(dept);
        return ApiResponse.success("DEPT", "CREATE", null);
    }

    @PutMapping("/{id}")
    @RequireRole({"ADMIN"})
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody SysDept dept) {
        dept.setId(id);
        sysDeptService.updateDept(dept);
        return ApiResponse.success("DEPT", "UPDATE", null);
    }

    @DeleteMapping("/{id}")
    @RequireRole({"ADMIN"})
    public ApiResponse<Void> delete(@PathVariable Long id) {
        sysDeptService.deleteDept(id);
        return ApiResponse.success("DEPT", "DELETE", null);
    }
}
