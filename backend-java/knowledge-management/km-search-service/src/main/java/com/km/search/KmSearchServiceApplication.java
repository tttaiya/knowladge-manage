package com.km.search;

import com.km.search.config.AiServiceProperties;
import com.km.search.config.InternalTokenProperties;
import com.km.search.config.RetrievalDefaultsProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@MapperScan("com.km.search.mapper")
@EnableConfigurationProperties({
        AiServiceProperties.class,
        InternalTokenProperties.class,
        RetrievalDefaultsProperties.class
})
public class KmSearchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(KmSearchServiceApplication.class, args);
    }
}

