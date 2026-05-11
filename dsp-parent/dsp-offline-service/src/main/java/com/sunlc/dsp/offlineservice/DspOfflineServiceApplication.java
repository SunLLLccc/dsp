package com.sunlc.dsp.offlineservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 离线导出服务启动类
 */
@SpringBootApplication(scanBasePackages = "com.sunlc.dsp")
@MapperScan("com.sunlc.dsp.mapper")
@EnableAsync
public class DspOfflineServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DspOfflineServiceApplication.class, args);
    }
}
