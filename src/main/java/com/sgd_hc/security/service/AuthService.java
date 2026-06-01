package com.sgd_hc.security.service;

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
