package com.sgd_hc.users.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.sgd_hc.users.dto.UserCreateDto;
import com.sgd_hc.users.dto.UserResponseDto;
import com.sgd_hc.users.dto.UserUpdateDto;
import com.sgd_hc.users.service.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/module_users/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public ResponseEntity<UserResponseDto> userCreate(@RequestBody UserCreateDto entity) {
        return new ResponseEntity<>(userService.createUser(entity), HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<Iterable<UserResponseDto>> getAllUser() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<UserResponseDto> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<UserResponseDto> userUpdate(@PathVariable UUID id, @RequestBody UserUpdateDto entity) {
        return ResponseEntity.ok(userService.updateUser(id, entity));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    public ResponseEntity<Void> userDelete(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
