package com.sgd_hc.sgd_hc.module_users.mapper;

import org.springframework.stereotype.Component;
import com.sgd_hc.sgd_hc.module_users.dto.PermissionCreateDto;
import com.sgd_hc.sgd_hc.module_users.dto.PermissionUpdateDto;
import com.sgd_hc.sgd_hc.module_users.dto.PermissionResponseDto;
import com.sgd_hc.sgd_hc.module_users.entity.Permission;

@Component
public class PermissionMapper {

    public Permission toEntity(PermissionCreateDto dto) {
        return Permission.builder()
                .name(dto.name())
                .module(dto.module())
                .action(dto.action())
                .description(dto.description())
                .build();
    }

    public void updateEntityFromDto(PermissionUpdateDto dto, Permission entity) {
        if (dto.name() != null) entity.setName(dto.name());
        if (dto.description() != null) entity.setDescription(dto.description());
        if (dto.active() != null) entity.setActive(dto.active());
    }

    public PermissionResponseDto toResponseDto(Permission entity) {
        return PermissionResponseDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .module(entity.getModule())
                .action(entity.getAction())
                .description(entity.getDescription())
                .active(entity.getActive())
                .build();
    }
}

