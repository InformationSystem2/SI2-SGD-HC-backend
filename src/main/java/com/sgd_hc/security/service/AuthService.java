package com.sgd_hc.security.service;

import com.sgd_hc.audit.annotation.Auditable;
import com.sgd_hc.audit.entity.enums.ActionType;
import com.sgd_hc.audit.aspect.AuditAspect;

import com.sgd_hc.users.entity.User;
import com.sgd_hc.users.repository.UserRepository;
import com.sgd_hc.security.dto.AuthRequestDto;
import com.sgd_hc.security.dto.AuthResponseDto;
import com.sgd_hc.security.dto.RefreshTokenRequestDto;
import com.sgd_hc.security.details.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate;
    private final com.sgd_hc.config.mail.EmailService emailService;

    @org.springframework.beans.factory.annotation.Value("${spring.profiles.active:prod}")
    private String activeProfile;

    @Auditable(resourceType = "AUTH", actionType = ActionType.LOGIN)    
    public AuthResponseDto login(AuthRequestDto requestDto) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        requestDto.username(),
                        requestDto.password()
                )
        );

        User user = userRepository.findByUsername(requestDto.username())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + requestDto.username()));

        AuditAspect.loginUserHolder.set(user);
                
        SecurityUser securityUser = new SecurityUser(user);
        String accessToken = jwtService.generateAccessToken(securityUser);
        String refreshToken = jwtService.generateRefreshToken(securityUser);

        return new AuthResponseDto(accessToken, refreshToken, jwtService.getJwtExpiration());
    }

    @Auditable(resourceType = "AUTH", actionType = ActionType.LOGIN)
    public AuthResponseDto refreshToken(RefreshTokenRequestDto requestDto) {
        String username = jwtService.extractUsername(requestDto.refreshToken());
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        SecurityUser securityUser = new SecurityUser(user);
        if (!jwtService.isTokenValid(requestDto.refreshToken(), securityUser)) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        String newAccessToken = jwtService.generateAccessToken(securityUser);
        String newRefreshToken = jwtService.generateRefreshToken(securityUser);

        return new AuthResponseDto(newAccessToken, newRefreshToken, jwtService.getJwtExpiration());
    }

    public com.sgd_hc.tenants.dto.SendCodeResponseDto sendPasswordRecoveryCode(com.sgd_hc.security.dto.ForgotPasswordRequestDto request) {
        String email = request.email();
        User user;
        
        com.sgd_hc.security.config.tenant.TenantContext.setBypassFilter(true);
        try {
            user = userRepository.findByEmail(email).orElse(null);
        } finally {
            com.sgd_hc.security.config.tenant.TenantContext.setBypassFilter(false);
        }
        
        if (user == null) {
            // No revelamos si existe o no, pero para dev env returnamos igual
            if ("dev".equalsIgnoreCase(activeProfile)) {
                return new com.sgd_hc.tenants.dto.SendCodeResponseDto("Usuario no encontrado (solo dev)", null);
            }
            return new com.sgd_hc.tenants.dto.SendCodeResponseDto("Si el correo existe, se enviará un código", null);
        }

        String code = String.format("%06d", new java.util.Random().nextInt(999999));
        String redisKey = "recovery_code:" + email;
        redisTemplate.opsForValue().set(redisKey, code, java.time.Duration.ofMinutes(10));

        if ("dev".equalsIgnoreCase(activeProfile)) {
            return new com.sgd_hc.tenants.dto.SendCodeResponseDto("Código generado para pruebas", code);
        } else {
            emailService.sendVerificationCode(email, code);
            return new com.sgd_hc.tenants.dto.SendCodeResponseDto("Si el correo existe, se enviará un código", null);
        }
    }

    public java.util.Map<String, String> resetPassword(com.sgd_hc.security.dto.VerifyRecoveryCodeRequestDto request) {
        String email = request.email();
        String redisKey = "recovery_code:" + email;
        String savedCode = redisTemplate.opsForValue().get(redisKey);

        if (savedCode == null || !savedCode.equals(request.code())) {
            throw new IllegalArgumentException("El código de verificación es incorrecto o ha expirado.");
        }

        User user;
        com.sgd_hc.security.config.tenant.TenantContext.setBypassFilter(true);
        try {
            user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
            user.setPassword(passwordEncoder.encode(request.newPassword()));
            userRepository.save(user);
        } finally {
            com.sgd_hc.security.config.tenant.TenantContext.setBypassFilter(false);
        }

        redisTemplate.delete(redisKey);

        return java.util.Map.of("message", "Contraseña actualizada exitosamente.");
    }
}
