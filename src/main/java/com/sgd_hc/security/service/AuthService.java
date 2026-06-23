package com.sgd_hc.security.service;

import com.sgd_hc.audit.annotation.Auditable;
import com.sgd_hc.audit.entity.enums.ActionType;
import com.sgd_hc.audit.aspect.AuditAspect;

import com.sgd_hc.users.entity.User;
import com.sgd_hc.users.repository.UserRepository;
import com.sgd_hc.config.mail.EmailService;
import com.sgd_hc.security.dto.AuthRequestDto;
import com.sgd_hc.security.dto.AuthResponseDto;
import com.sgd_hc.security.dto.RefreshTokenRequestDto;
import com.sgd_hc.security.details.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import com.sgd_hc.tenants.dto.SendCodeResponseDto;
import com.sgd_hc.security.dto.ForgotPasswordRequestDto;
import com.sgd_hc.security.config.tenant.TenantContext;
import com.sgd_hc.security.dto.VerifyRecoveryCodeRequestDto;

import com.sgd_hc.security.dto.ProfileResponseDto;
import com.sgd_hc.security.dto.ProfileUpdateDto;
import com.sgd_hc.security.dto.PasswordChangeDto;
import com.sgd_hc.users.entity.DocumentType;

import java.time.Duration;
import java.util.Random;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate;
    private final EmailService emailService;

    @Value("${spring.profiles.active}")
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

    public SendCodeResponseDto sendPasswordRecoveryCode(ForgotPasswordRequestDto request) {
        String email = request.email();
        User user;
        
        TenantContext.setBypassFilter(true);
        try {
            user = userRepository.findByEmail(email).orElse(null);
        } finally {
            TenantContext.setBypassFilter(false);
        }
        
        if (user == null) {
            // No revelamos si existe o no, pero para dev env returnamos igual
            if ("dev".equalsIgnoreCase(activeProfile)) {
                return new SendCodeResponseDto("Usuario no encontrado (solo dev)", null);
            }
            return new SendCodeResponseDto("Si el correo existe, se enviará un código", null);
        }

        String code = String.format("%06d", new Random().nextInt(999999));
        String redisKey = "recovery_code:" + email;
        redisTemplate.opsForValue().set(redisKey, code, Duration.ofMinutes(10));

        if ("dev".equalsIgnoreCase(activeProfile)) {
            return new SendCodeResponseDto("Código generado para pruebas", code);
        } else {
            emailService.sendVerificationCode(email, code);
            return new SendCodeResponseDto("Si el correo existe, se enviará un código", null);
        }
    }

    public Map<String, String> resetPassword(VerifyRecoveryCodeRequestDto request) {
        String email = request.email();
        String redisKey = "recovery_code:" + email;
        String savedCode = redisTemplate.opsForValue().get(redisKey);

        if (savedCode == null || !savedCode.equals(request.code())) {
            throw new IllegalArgumentException("El código de verificación es incorrecto o ha expirado.");
        }

        User user;
        TenantContext.setBypassFilter(true);
        try {
            user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
            user.setPassword(passwordEncoder.encode(request.newPassword()));
            userRepository.save(user);
        } finally {
            TenantContext.setBypassFilter(false);
        }

        redisTemplate.delete(redisKey);

        return Map.of("message", "Contraseña actualizada exitosamente.");
    }

    public ProfileResponseDto getProfile(String username) {
        User user;
        TenantContext.setBypassFilter(true);
        try {
            user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        } finally {
            TenantContext.setBypassFilter(false);
        }
        return new ProfileResponseDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                user.getDocumentType() != null ? user.getDocumentType().name() : null,
                user.getDocumentNumber(),
                user.getGender()
        );
    }

    public ProfileResponseDto updateProfile(String username, ProfileUpdateDto dto) {
        User user;
        TenantContext.setBypassFilter(true);
        try {
            user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        } finally {
            TenantContext.setBypassFilter(false);
        }

        if (!user.getEmail().equalsIgnoreCase(dto.email())) {
            boolean emailExists;
            TenantContext.setBypassFilter(true);
            try {
                emailExists = userRepository.existsByEmail(dto.email());
            } finally {
                TenantContext.setBypassFilter(false);
            }
            if (emailExists) {
                throw new IllegalArgumentException("El correo electrónico ya está en uso.");
            }
        }

        user.setFirstName(dto.firstName());
        user.setLastName(dto.lastName());
        user.setEmail(dto.email());
        user.setPhone(dto.phone());
        user.setGender(dto.gender());
        if (dto.documentType() != null) {
            user.setDocumentType(DocumentType.valueOf(dto.documentType()));
        }
        user.setDocumentNumber(dto.documentNumber());

        userRepository.save(user);

        return new ProfileResponseDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                user.getDocumentType() != null ? user.getDocumentType().name() : null,
                user.getDocumentNumber(),
                user.getGender()
        );
    }

    public Map<String, String> changePassword(String username, PasswordChangeDto dto) {
        User user;
        TenantContext.setBypassFilter(true);
        try {
            user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        } finally {
            TenantContext.setBypassFilter(false);
        }

        if (!passwordEncoder.matches(dto.currentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("La contraseña actual es incorrecta.");
        }

        user.setPassword(passwordEncoder.encode(dto.newPassword()));
        userRepository.save(user);

        return Map.of("message", "Contraseña actualizada exitosamente.");
    }
}
