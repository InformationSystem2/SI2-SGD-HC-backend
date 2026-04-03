package com.sgd_hc.sgd_hc.security.service;

import com.sgd_hc.sgd_hc.module_users.entity.User;
import com.sgd_hc.sgd_hc.module_users.repository.UserRepository;
import com.sgd_hc.sgd_hc.security.dto.AuthRequestDto;
import com.sgd_hc.sgd_hc.security.dto.AuthResponseDto;
import com.sgd_hc.sgd_hc.security.dto.RefreshTokenRequestDto;
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

    public AuthResponseDto login(AuthRequestDto requestDto) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        requestDto.username(),
                        requestDto.password()
                )
        );

        User user = userRepository.findByUsername(requestDto.username())
                .orElseThrow(() -> new UsernameNotFoundException("User not found" + requestDto.username()));

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        return new AuthResponseDto(accessToken, refreshToken, jwtService.getJwtExpiration());
    }

    public AuthResponseDto refreshToken(RefreshTokenRequestDto requestDto) {
        String username = jwtService.extractUsername(requestDto.refreshToken());
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found" + username));

        if (!jwtService.isTokenValid(requestDto.refreshToken(), user)) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user);

        return new AuthResponseDto(newAccessToken, newRefreshToken, jwtService.getJwtExpiration());
    }
}
