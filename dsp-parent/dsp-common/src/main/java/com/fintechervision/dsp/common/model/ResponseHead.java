package com.fintechervision.dsp.common.model;

import lombok.Data;

/**
 * 统一响应报文头
 */
@Data
public class ResponseHead {

    private String transno;
    private String traceId;
    private String timestamp;

    public static ResponseHead of(String transno, String traceId) {
        ResponseHead head = new ResponseHead();
        head.setTransno(transno);
        head.setTraceId(traceId);
        head.timestamp = java.time.LocalDateTime.now().toString();
        return head;
    }
}
