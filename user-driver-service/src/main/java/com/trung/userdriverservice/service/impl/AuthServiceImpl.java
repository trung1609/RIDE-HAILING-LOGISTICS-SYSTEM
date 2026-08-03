package com.trung.userdriverservice.service.impl;

import com.trung.userdriverservice.repository.UserRepository;
import com.trung.userdriverservice.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
}
