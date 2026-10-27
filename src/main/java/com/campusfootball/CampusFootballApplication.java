package com.campusfootball;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan(basePackages = "com.campusfootball", annotationClass = Mapper.class)
public class CampusFootballApplication {
    public static void main(String[] args) {
        SpringApplication.run(CampusFootballApplication.class, args);
    }
}
