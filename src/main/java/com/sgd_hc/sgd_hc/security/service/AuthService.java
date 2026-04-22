package com.sgd_hc.sgd_hc.security.service;

import java.util.HashSet;

import com.sgd_hc.sgd_hc.module_users.entity.User;
import com.sgd_hc.sgd_hc.module_users.repository.UserRepository;
import com.sgd_hc.sgd_hc.security.dto.AuthRequestDto;
import com.sgd_hc.sgd_hc.security.dto.AuthResponseDto;
import com.sgd_hc.sgd_hc.security.dto.RefreshTokenRequestDto;
import com.sgd_hc.sgd_hc.security.dto.RegisterRequestDto;
import com.sgd_hc.sgd_hc.security.details.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;

    public AuthResponseDto login(AuthRequestDto requestDto) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        requestDto.username(),
                        requestDto.password()
                )
        );

        User user = userRepository.findByUsername(requestDto.username())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + requestDto.username()));

        SecurityUser securityUser = new SecurityUser(user);
        String accessToken = jwtService.generateAccessToken(securityUser);
        String refreshToken = jwtService.generateRefreshToken(securityUser);

        return new AuthResponseDto(accessToken, refreshToken, jwtService.getJwtExpiration());
    }

    @Transactional
    public void register(RegisterRequestDto dto) {
        if (userRepository.existsByEmail(dto.email()))
            throw new IllegalArgumentException("Email already exists");

        String username;
        do {
            username = "USR-" + ((int)(Math.random() * 9000) + 1000);
        } while (userRepository.existsByUsername(username));

        userRepository.save(User.builder()
                .username(username)
                .email(dto.email())
                .firstName(dto.firstName())
                .lastName(dto.lastName())
                .password(passwordEncoder.encode(dto.password()))
                .documentType(dto.documentType())
                .documentNumber(dto.documentNumber())
                .phone(dto.phone())
                .gender(dto.gender() != null ? dto.gender() : "unknown")
                .isActive(true)
                .roles(new HashSet<>())
                .build());
    }

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
}
