package com.yussufajao.gateway.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "secuirty.jwt")
public record JwtProperties(
    @NotBlank String issuer,
    @NotBlank String audience 
){}