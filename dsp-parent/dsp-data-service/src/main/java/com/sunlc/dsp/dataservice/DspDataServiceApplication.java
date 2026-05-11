package com.sunlc.dsp.dataservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 数据服务启动类
 */
@SpringBootApplication(scanBasePackages = "com.sunlc.dsp")
@MapperScan("com.sunlc.dsp.mapper")
@EnableAsync
@EnableScheduling
public class DspDataServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DspDataServiceApplication.class, args);
    }
}
