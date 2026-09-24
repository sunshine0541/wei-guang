package com.weiguang.modules.auth.service;

import com.weiguang.modules.system.service.ConfigService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * JWT 签发与校验（详设 §3.1）：HS256，密钥取环境变量 JWT_SECRET，有效期取 sys_config jwt.expire_days。
 * claim：uid + identity（user / admin）。
 */
@Service
public class JwtService {

    public static final String IDENTITY_USER = "user";
    public static final String IDENTITY_ADMIN = "admin";

    private static final String CLAIM_UID = "uid";
    private static final String CLAIM_IDENTITY = "identity";

    private final SecretKey key;
    private final ConfigService configService;

    public JwtService(@Value("${JWT_SECRET:}") String secret, ConfigService configService) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET 未配置：请在 backend/.env 中设置后重启（密钥永不入 sys_config）");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET 长度不足 32 字节，HS256 要求至少 256 位密钥");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.configService = configService;
    }

    public String issue(Long id, String identity) {
        int expireDays = configService.getInt("jwt.expire_days", 7);
        Instant now = Instant.now();
        return Jwts.builder()
                .claim(CLAIM_UID, id)
                .claim(CLAIM_IDENTITY, identity)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expireDays, ChronoUnit.DAYS)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** 解析并校验身份，identity 不匹配视为凭证无效 */
    public Long parseId(String token, String expectedIdentity) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        if (!expectedIdentity.equals(claims.get(CLAIM_IDENTITY))) {
            throw new JwtException("identity 不匹配");
        }
        Object uid = claims.get(CLAIM_UID);
        if (!(uid instanceof Number number)) {
            throw new JwtException("uid 缺失");
        }
        return number.longValue();
    }
}
