package com.trung.userdriverservice.dto.response;

import com.trung.userdriverservice.util.enums.Role;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class UserPaymentInfoResponse {
    private Long userId;
    private String fullName;
    private String phoneNumber;
    private String email;
    private Role role;
}