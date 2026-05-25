package org.example.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(Map<String, String> routes) {
}
