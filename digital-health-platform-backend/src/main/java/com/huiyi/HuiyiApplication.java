package com.huiyi;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@MapperScan("com.huiyi.modules.**.mapper")
public class HuiyiApplication {
    public static void main(String[] args) {
        SpringApplication.run(HuiyiApplication.class, args);
    }
}
