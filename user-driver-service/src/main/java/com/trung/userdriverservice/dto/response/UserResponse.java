package com.trung.userdriverservice.dto.response;

import com.trung.userdriverservice.util.enums.Role;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class UserResponse {
    private Long id;
    private String phoneNumber;
    private String email;
    private String fullName;
    private Role role;
    private LocalDateTime createdAt;
}
