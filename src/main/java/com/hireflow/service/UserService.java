package com.hireflow.service;

import com.hireflow.domain.Organisation;
import com.hireflow.domain.User;
import com.hireflow.domain.enums.Role;
import com.hireflow.exception.ConflictException;
import com.hireflow.exception.ResourceNotFoundException;
import com.hireflow.exception.ValidationException;
import com.hireflow.repository.OrganisationRepository;
import com.hireflow.repository.UserRepository;
import com.hireflow.web.controller.UserController.CreateUserRequest;
import com.hireflow.web.controller.UserController.UserResponse;
import com.hireflow.web.dto.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final OrganisationRepository orgRepository;
    private final PasswordEncoder passwordEncoder;

    static final String DEFAULT_PASSWORD = "Welcome@2026";

    public UserResponse create(CreateUserRequest request) {
        UUID orgId = SecurityUtils.currentOrgId();
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already registered: " + request.email());
        }
        Organisation org = orgRepository.getReferenceById(orgId);
        User user = User.builder()
                .organisation(org)
                .email(request.email())
                .passwordHash(passwordEncoder.encode(DEFAULT_PASSWORD))
                .fullName(request.fullName())
                .role(request.role())
                .enabled(true)
                .mustChangePassword(true)
                .build();
        return toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(Pageable pageable) {
        UUID orgId = SecurityUtils.currentOrgId();
        return PageResponse.of(userRepository.findByOrganisationId(orgId, pageable).map(this::toResponse));
    }

    public UserResponse setEnabled(UUID id, boolean enabled) {
        UUID orgId = SecurityUtils.currentOrgId();
        User user = userRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        user.setEnabled(enabled);
        return toResponse(user);
    }

    public void resetPassword(UUID id, String newPassword) {
        UUID orgId = SecurityUtils.currentOrgId();
        User user = userRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(true);
    }

    public void changePassword(String currentPassword, String newPassword) {
        UUID userId = SecurityUtils.currentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ValidationException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getRole(), user.isEnabled());
    }
}
