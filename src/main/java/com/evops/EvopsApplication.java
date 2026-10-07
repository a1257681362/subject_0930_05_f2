package com.evops;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// 未使用 Shiro 注解鉴权，排除其注解处理器（注册 JDK 动态代理的 AutoProxyCreator），
// 事务由 Spring Boot 默认的 CGLIB 代理驱动；Shiro 过滤器链不受影响。
@EnableScheduling
@MapperScan("com.evops.plant.mapper")
@SpringBootApplication(excludeName = {
        "org.apache.shiro.spring.boot.autoconfigure.ShiroAnnotationProcessorAutoConfiguration"
})
public class EvopsApplication {
    public static void main(String[] args) {
        SpringApplication.run(EvopsApplication.class, args);
    }
}
