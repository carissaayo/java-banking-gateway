package com.yussufajao.gateway.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.cors")
public record GatewayCorsProperties(List<String> allowedOrigins){

    public GatewayCorsProperties{
        if (allowedOrigins == null){
            allowedOrigins= List.of();
        }else{
            allowedOrigins = allowedOrigins.stream()
                    .filter(origin -> origin != null && !origin.isBlank())
                    .map(String::trim)
                    .toList();
        }
        for(String origin : allowedOrigins){
            if("*".equals(origin)){
                throw new IllegalArgumentException("gateway.cors.allowed-origins must not contain *");
            }
        }
    }
}