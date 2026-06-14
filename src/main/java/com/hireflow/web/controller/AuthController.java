package com.hireflow.web.controller;

import com.hireflow.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    public record LoginRequest(@NotBlank @Email String email,
                               @NotBlank String password) { }

    public record RefreshRequest(@NotBlank String refreshToken) { }

    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @jakarta.validation.constraints.Size(min = 10, max = 100) String newPassword) { }

    public record TokenResponse(String accessToken,
                                String refreshToken,
                                String tokenType,
                                long expiresInSeconds,
                                boolean mustChangePassword) { }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        authService.logout();
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request.currentPassword(), request.newPassword());
    }
}
