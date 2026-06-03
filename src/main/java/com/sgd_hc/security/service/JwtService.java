package com.sgd_hc.security.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import com.sgd_hc.security.details.SecurityUser;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Getter
    @Value("${jwt.expiration}")
    private Long jwtExpiration;

    @Value("${jwt.refresh-expiration}")
    private Long refreshExpiration;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractTenantId(String token) {
        return extractClaim(token, claims -> claims.get("tenantId", String.class));
    }

    public String extractTenantSlug(String token) {
        return extractClaim(token, claims -> claims.get("tenantSlug", String.class));
    }

    public Instant extractTenantSuspendedAt(String token) {
        String suspendedAtStr = extractClaim(token, claims -> claims.get("tenantSuspendedAt", String.class));
        if (suspendedAtStr == null || suspendedAtStr.isBlank()) {
            return null;
        }
        return Instant.parse(suspendedAtStr);
    }

    public String extractJti(String token) {
        return extractClaim(token, claims -> claims.getId());
    }

    public Instant extractIssuedAt(String token) {
        return extractClaim(token, Claims::getIssuedAt).toInstant();
    }

    public <T> T extractClaim(String token, @NonNull Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    public String generateAccessToken(@NonNull UserDetails userDetails) {
        return generateAccessToken(userDetails, null);
    }

    public String generateAccessToken(@NonNull UserDetails userDetails, Instant tenantSuspendedAt) {
        Map<String, Object> extraClaims = new HashMap<>();
        
        java.util.List<String> roles;
        if (userDetails instanceof SecurityUser su) {
            roles = su.getUser().getRoles().stream()
                    .map(com.sgd_hc.users.entity.Role::getName)
                    .toList();
            extraClaims.put("userId", su.getUser().getId().toString());
        } else {
            roles = userDetails.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(auth -> auth.startsWith("ROLE_"))
                    .toList();
        }
        extraClaims.put("roles", roles);
        extraClaims.put("iat", Instant.now().toString());

        if (userDetails instanceof SecurityUser su) {
            extraClaims.put("tenantId", su.getUser().getTenant().getId().toString());
            extraClaims.put("tenantSlug", su.getUser().getTenant().getSlug());

            TenantSuspensionInfo suspensionInfo = buildSuspensionInfo(su.getUser().getTenant().getId(), tenantSuspendedAt);
            extraClaims.put("tenantSuspendedAt", suspensionInfo.suspendedAt().toString());
            extraClaims.put("tenantIdForRevocation", suspensionInfo.tenantId().toString());
        }

        return buildToken(extraClaims, userDetails, jwtExpiration);
    }

    private TenantSuspensionInfo buildSuspensionInfo(UUID tenantId, Instant forcedSuspendedAt) {
        if (forcedSuspendedAt != null) {
            return new TenantSuspensionInfo(tenantId, forcedSuspendedAt);
        }
        return new TenantSuspensionInfo(tenantId, Instant.MAX);
    }

    private record TenantSuspensionInfo(UUID tenantId, Instant suspendedAt) {}

    public String generateRefreshToken(UserDetails userDetails) {
        return buildToken(new HashMap<>(), userDetails, refreshExpiration);
    }

    public boolean isTokenValid(String token, @NonNull UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    public long getExpirationEpochSecond(String token) {
        return extractClaim(token, Claims::getExpiration).getTime() / 1000;
    }

    private String buildToken(Map<String, Object> extraClaims, @NonNull UserDetails userDetails, Long expiration) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private @NonNull SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}