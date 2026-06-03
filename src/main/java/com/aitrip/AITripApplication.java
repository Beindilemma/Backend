package com.aitrip;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.aitrip.mapper")
public class AITripApplication {
    public static void main(String[] args) {
        SpringApplication.run(AITripApplication.class, args);
        System.out.println("=====================================");
        System.out.println("AI途迹项目启动成功！");
        System.out.println("=====================================");
    }
}