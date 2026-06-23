package com.sgd_hc.security.controller;

import com.sgd_hc.security.dto.AuthRequestDto;
import com.sgd_hc.security.dto.AuthResponseDto;
import com.sgd_hc.security.dto.RefreshTokenRequestDto;
import com.sgd_hc.security.dto.ForgotPasswordRequestDto;
import com.sgd_hc.security.dto.VerifyRecoveryCodeRequestDto;
import com.sgd_hc.security.service.AuthService;
import com.sgd_hc.tenants.dto.SendCodeResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.GrantedAuthority;
import java.util.Collection;
import java.util.Map;
import com.sgd_hc.security.dto.ProfileResponseDto;
import com.sgd_hc.security.dto.ProfileUpdateDto;
import com.sgd_hc.security.dto.PasswordChangeDto;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody AuthRequestDto request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDto> refresh(@Valid @RequestBody RefreshTokenRequestDto request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    @PostMapping("/public/forgot-password")
    public ResponseEntity<SendCodeResponseDto> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequestDto request) {
        return ResponseEntity.ok(authService.sendPasswordRecoveryCode(request));
    }

    @PostMapping("/public/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody VerifyRecoveryCodeRequestDto request) {
        return ResponseEntity.ok(authService.resetPassword(request));
    }

    @GetMapping("/me/permissions")
    public ResponseEntity<Collection<String>> getMyPermissions(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        Collection<String> authorities = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return ResponseEntity.ok(authorities);
    }

    @GetMapping("/profile")
    public ResponseEntity<ProfileResponseDto> getProfile(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(authService.getProfile(userDetails.getUsername()));
    }

    @PutMapping("/profile")
    public ResponseEntity<ProfileResponseDto> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ProfileUpdateDto request) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(authService.updateProfile(userDetails.getUsername(), request));
    }

    @PutMapping("/profile/password")
    public ResponseEntity<Map<String, String>> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody PasswordChangeDto request) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(authService.changePassword(userDetails.getUsername(), request));
    }
}