package dev.hidly.nubi.auth.controller;

import dev.hidly.nubi.auth.data.dto.request.LoginRequestDto;
import dev.hidly.nubi.auth.data.dto.request.RefreshRequestDto;
import dev.hidly.nubi.auth.data.dto.request.SignUpRequestDto;
import dev.hidly.nubi.auth.data.dto.response.TokenResponseDto;
import dev.hidly.nubi.auth.service.AuthService;
import dev.hidly.nubi.common.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/signup")
    @Operation(summary = "기관 가입", description = "기관과 첫 담당자(ADMIN) 계정을 만들고 토큰을 발급합니다.")
    public GlobalResponse<TokenResponseDto> signUp(@Valid @RequestBody SignUpRequestDto request) {
        return authService.signUp(request);
    }

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "액세스, 리프레시 토큰을 발급합니다.")
    public GlobalResponse<TokenResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "토큰 재발급", description = "리프레시 토큰으로 액세스, 리프레시 토큰을 다시 발급합니다.")
    public GlobalResponse<TokenResponseDto> refresh(@Valid @RequestBody RefreshRequestDto request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃")
    public GlobalResponse<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken) {
        return authService.logout(bearerToken);
    }
}
