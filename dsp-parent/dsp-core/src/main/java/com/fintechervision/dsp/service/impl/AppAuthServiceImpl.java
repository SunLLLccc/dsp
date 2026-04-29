package com.fintechervision.dsp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fintechervision.dsp.common.util.JwtUtil;
import com.fintechervision.dsp.entity.AppAuth;
import com.fintechervision.dsp.mapper.AppAuthMapper;
import com.fintechervision.dsp.service.AppAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppAuthServiceImpl extends ServiceImpl<AppAuthMapper, AppAuth> implements AppAuthService {

    private final JwtUtil jwtUtil;

    @Override
    public List<AppAuth> listAll() {
        LambdaQueryWrapper<AppAuth> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(AppAuth::getCreatedTime);
        return list(wrapper);
    }

    @Override
    public Map<String, Object> generateToken(String appId) {
        LambdaQueryWrapper<AppAuth> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppAuth::getAppId, appId).eq(AppAuth::getStatus, 1);
        AppAuth app = getOne(wrapper);
        if (app == null) throw new RuntimeException("应用不存在或已禁用");
        List<String> allowedTransnos;
        if (app.getAllowedTransnos() != null && !app.getAllowedTransnos().trim().isEmpty()) {
            allowedTransnos = Arrays.asList(app.getAllowedTransnos().split(","));
        } else {
            allowedTransnos = Arrays.asList("*");
        }
        String token = jwtUtil.generateToken(appId, allowedTransnos);
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("appId", appId);
        result.put("allowedTransnos", allowedTransnos);
        return result;
    }
}
