package com.fintechervision.dsp.dataservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 数据服务启动类
 */
@SpringBootApplication(scanBasePackages = "com.fintechervision.dsp")
@MapperScan("com.fintechervision.dsp.mapper")
@EnableAsync
public class DspDataServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DspDataServiceApplication.class, args);
    }
}
