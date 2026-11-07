package com.DatShop.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
@Getter
@Setter
public class JwtProperties {

    /**
     * Base64-encoded HMAC secret. Must decode to at least 32 bytes.
     * Supplied via JWT_SECRET or application-local.properties. Never hardcode.
     */
    private String secret = "";

    /** Access-token lifetime in milliseconds. Default is 24 hours. */
    private long expirationMs = 86_400_000L;
}
