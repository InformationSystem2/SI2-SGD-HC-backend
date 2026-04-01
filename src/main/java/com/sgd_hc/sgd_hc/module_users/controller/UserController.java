package com.sgd_hc.sgd_hc.module_users.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sgd_hc.sgd_hc.module_users.dto.UserCreateDto;
import com.sgd_hc.sgd_hc.module_users.dto.UserResponseDto;
import com.sgd_hc.sgd_hc.module_users.dto.UserUpdateDto;
import com.sgd_hc.sgd_hc.module_users.service.UserService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/module_users/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserResponseDto> userCreate(@RequestBody UserCreateDto entity) {
        return new ResponseEntity<>(userService.createUser(entity), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<Iterable<UserResponseDto>> getAllUser() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDto> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponseDto> userUpdate(@PathVariable Long id, @RequestBody UserUpdateDto entity) {
        return ResponseEntity.ok(userService.updateUser(id, entity));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> userDelete(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

}
