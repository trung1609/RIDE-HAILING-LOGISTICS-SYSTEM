package com.trung.userdriverservice.controller;
import com.trung.userdriverservice.dto.request.PageRequestDTO;
import com.trung.userdriverservice.dto.request.UserRegisterRequest;
import com.trung.userdriverservice.dto.response.ApiResponse;
import com.trung.userdriverservice.dto.response.PageResponseDTO;
import com.trung.userdriverservice.dto.response.UserResponse;
import com.trung.userdriverservice.exception.ResourceConflictException;
import com.trung.userdriverservice.exception.ResourceNotFoundException;
import com.trung.userdriverservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register/customer")
    public ResponseEntity<ApiResponse<UserResponse>> registerCustomer(@Valid @RequestBody UserRegisterRequest request) throws ResourceConflictException {

        ApiResponse<UserResponse> response = ApiResponse.<UserResponse>builder()
                .success(true)
                .message("Đăng ký tài khoản khách hàng thành công")
                .data(userService.registerCustomer(request).getData())
                .timestamp(LocalDateTime.now())
                .build();

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponseDTO<UserResponse>> getAllUser(@ModelAttribute PageRequestDTO requestDTO) throws ResourceNotFoundException {
        return ResponseEntity.ok(userService.getAllUsers(requestDTO));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or authentication.principal.user.id = #id")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Long id) throws ResourceNotFoundException {
        return ResponseEntity.ok(userService.getUserById(id));
    }
}
