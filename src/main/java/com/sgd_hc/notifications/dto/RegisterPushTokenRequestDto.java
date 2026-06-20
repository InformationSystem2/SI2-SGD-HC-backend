package com.sgd_hc.notifications.dto;

import com.sgd_hc.notifications.entity.PushPlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterPushTokenRequestDto(
        @NotBlank String token,
        @NotNull PushPlatform platform
) {}
