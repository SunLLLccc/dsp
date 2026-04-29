package com.fintechervision.dsp.dataservice.handler;

import com.fintechervision.dsp.common.model.ApiResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@ControllerAdvice(basePackages = "com.fintechervision.dsp.dataservice.controller")
public class GlobalResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return !ApiResponse.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        String path = request.getURI().getPath();
        String transno = "UNKNOWN";
        if (path.contains("/dsp/api/")) {
            transno = path.substring(path.lastIndexOf("/") + 1);
        }

        if (body == null) {
            return ApiResponse.success(transno, "", null);
        }

        if (body instanceof ApiResponse) {
            return body;
        }

        return ApiResponse.success(transno, "", body);
    }
}
