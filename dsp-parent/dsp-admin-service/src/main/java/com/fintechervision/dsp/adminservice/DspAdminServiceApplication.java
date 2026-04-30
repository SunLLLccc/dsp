package com.fintechervision.dsp.adminservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 管理平台后端服务启动类
 */
@SpringBootApplication(scanBasePackages = "com.fintechervision.dsp")
@MapperScan("com.fintechervision.dsp.mapper")
@EnableScheduling
public class DspAdminServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DspAdminServiceApplication.class, args);
    }
}
