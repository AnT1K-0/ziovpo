package com.example.shop.security;

import com.example.shop.model.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtTokenProvider {

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_SID = "sid";
    private static final String CLAIM_ROLE = "role";

    private final UserDetailsService userDetailsService;
    private final Key key;
    private final long accessTokenValidityMs;
    private final long refreshTokenValidityMs;

    public JwtTokenProvider(
            UserDetailsService userDetailsService,
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration-ms:900000}") long accessTokenValidityMs,
            @Value("${app.jwt.refresh-token-expiration-ms:604800000}") long refreshTokenValidityMs
    ) {
        this.userDetailsService = userDetailsService;
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidityMs = accessTokenValidityMs;
        this.refreshTokenValidityMs = refreshTokenValidityMs;
    }

    public String generateAccessToken(UserAccount user, Long sessionId) {
        return buildAccessToken(user, sessionId);
    }

    public String generateRefreshToken(UserAccount user, Long sessionId) {
        return buildRefreshToken(user, sessionId);
    }

    private String buildAccessToken(UserAccount user, Long sessionId) {
        Instant now = Instant.now();
        Date issuedAt = Date.from(now);
        Date expiry = Date.from(now.plusMillis(accessTokenValidityMs));

        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_TYPE, "ACCESS");
        claims.put(CLAIM_SID, sessionId);
        // role кладём только в ACCESS (для демонстрации, но фильтр всё равно берёт роли из БД)
        claims.put(CLAIM_ROLE, user.getRole());

        return Jwts.builder()
                .setSubject(user.getUsername())
                .setIssuedAt(issuedAt)
                .setExpiration(expiry)
                .addClaims(claims)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private String buildRefreshToken(UserAccount user, Long sessionId) {
        Instant now = Instant.now();
        Date issuedAt = Date.from(now);
        Date expiry = Date.from(now.plusMillis(refreshTokenValidityMs));

        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_TYPE, "REFRESH");
        claims.put(CLAIM_SID, sessionId);


        return Jwts.builder()
                .setSubject(user.getUsername())
                .setIssuedAt(issuedAt)
                .setExpiration(expiry)
                .addClaims(claims)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private boolean validateToken(String token, String expectedType) {
        try {
            Claims claims = parseClaims(token);

            String type = claims.get(CLAIM_TYPE, String.class);
            if (!expectedType.equals(type)) {
                return false;
            }

            Date exp = claims.getExpiration();
            return exp != null && exp.after(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean validateAccessToken(String token) {
        return validateToken(token, "ACCESS");
    }

    public boolean validateRefreshToken(String token) {
        return validateToken(token, "REFRESH");
    }

    public Long getSessionId(String token) {
        Object sid = parseClaims(token).get(CLAIM_SID);
        if (sid == null) return null;
        if (sid instanceof Integer i) return i.longValue();
        if (sid instanceof Long l) return l;
        if (sid instanceof String s) return Long.parseLong(s);
        return null;
    }

    public Date getExpiration(String token) {
        return parseClaims(token).getExpiration();
    }

    public String getUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public Authentication getAuthentication(String token) {
        String username = getUsername(token);
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        return new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities()
        );
    }
}
