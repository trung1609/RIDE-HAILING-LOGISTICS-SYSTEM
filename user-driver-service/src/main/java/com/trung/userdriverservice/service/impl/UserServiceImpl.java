package com.trung.userdriverservice.service.impl;

import com.trung.userdriverservice.dto.request.PageRequestDTO;
import com.trung.userdriverservice.dto.request.UserRegisterRequest;
import com.trung.userdriverservice.dto.response.ApiResponse;
import com.trung.userdriverservice.dto.response.PageResponseDTO;
import com.trung.userdriverservice.dto.response.UserResponse;
import com.trung.userdriverservice.entity.User;
import com.trung.userdriverservice.exception.ResourceConflictException;
import com.trung.userdriverservice.exception.ResourceNotFoundException;
import com.trung.userdriverservice.mapper.UserMapper;
import com.trung.userdriverservice.repository.UserRepository;
import com.trung.userdriverservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public ApiResponse<UserResponse> registerCustomer(UserRegisterRequest request) throws ResourceConflictException {
        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new ResourceConflictException("Số điện thoại đã được đăng ký.");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResourceConflictException("Email đã được đăng ký.");
        }

        User user = userMapper.toCustomerEntity(request);

        User savedUser = userRepository.save(user);

        return ApiResponse.<UserResponse>builder()
                .data(userMapper.toUserResponse(savedUser))
                .build();
    }

    @Override
    public PageResponseDTO<UserResponse> getAllUsers(PageRequestDTO pageRequestDTO) {
        int size = pageRequestDTO.getSize() != null ? pageRequestDTO.getSize() : 10;
        int page = pageRequestDTO.getPage() != null ? pageRequestDTO.getPage() : 0;
        String sortBy = pageRequestDTO.getSortBy() != null ? pageRequestDTO.getSortBy() : "id";
        Sort.Direction direction = pageRequestDTO.getSortDirection() != null ? Sort.Direction.fromString(pageRequestDTO.getSortDirection()) : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        Page<User> userPage = userRepository.findAll(pageable);
        return PageResponseDTO.<UserResponse>builder()
                .page(userPage.getNumber())
                .size(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .content(userPage.map(userMapper::toUserResponse).getContent())
                .hasNext(userPage.hasNext())
                .hasPrevious(userPage.hasPrevious())
                .build();
    }

    @Override
    public ApiResponse<UserResponse> getUserById(Long id) throws ResourceNotFoundException {
        return ApiResponse.<UserResponse>builder()
                .data(userMapper.toUserResponse(userRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với id: " + id))))
                .build();
    }

    @Override
    public void lockUserAccount(Long userId) throws ResourceNotFoundException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với id: " + userId));
        // Ràng buộc bổ sung: Có thể kiểm tra xem user này có cuốc xe nào chưa thanh toán không trước khi khóa
        user.setIsDeleted(true);
        userRepository.save(user);
    }
}
