package com.sunlc.dsp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunlc.dsp.common.enums.ErrorCode;
import com.sunlc.dsp.common.exception.BusinessException;
import com.sunlc.dsp.common.util.JwtUtil;
import com.sunlc.dsp.entity.AppAuth;
import com.sunlc.dsp.enums.CommonStatus;
import com.sunlc.dsp.mapper.AppAuthMapper;
import com.sunlc.dsp.service.AppAuthService;
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
        wrapper.eq(AppAuth::getAppId, appId).eq(AppAuth::getStatus, CommonStatus.ENABLED.getCode());
        AppAuth app = getOne(wrapper);
        if (app == null) throw new BusinessException(ErrorCode.APP_NOT_FOUND);
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
