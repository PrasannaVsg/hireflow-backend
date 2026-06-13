package com.hireflow.web.controller;

import com.hireflow.domain.enums.Role;
import com.hireflow.service.UserService;
import com.hireflow.web.dto.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    public record CreateUserRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 10, max = 100) String password,
            @NotBlank @Size(max = 120) String fullName,
            @NotNull Role role) { }

    public record UserResponse(UUID id, String email, String fullName, Role role, boolean enabled) { }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @GetMapping
    public PageResponse<UserResponse> list(Pageable pageable) {
        return userService.list(pageable);
    }

    @PatchMapping("/{id}/disable")
    public UserResponse disable(@PathVariable UUID id) {
        return userService.setEnabled(id, false);
    }
}
