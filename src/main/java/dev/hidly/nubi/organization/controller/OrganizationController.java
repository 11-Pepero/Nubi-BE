package dev.hidly.nubi.organization.controller;

import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.organization.data.dto.response.MyOrganizationResponseDto;
import dev.hidly.nubi.organization.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import dev.hidly.nubi.organization.data.dto.request.MyOrganizationUpdateRequestDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증과 기관", description = "기관 가입, 로그인, 토큰 재발급")
@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {
    private final OrganizationService organizationService;

    @GetMapping("/me")
    @Operation(summary = "내 기관 조회", description = "로그인한 담당자의 기관 정보와 담당자 정보입니다. 사이드바 아래 기관 이름에 씁니다.")
    public GlobalResponse<MyOrganizationResponseDto> getMyOrganization() {
        return organizationService.getMyOrganization();
    }

    @PatchMapping("/me")
    @Operation(summary = "내 기관 수정", description = "기관 관리자(ADMIN)만 고칠 수 있습니다. 보낸 값(name, address, phone)만 바뀌고, 주소를 바꾸면 구(district)도 다시 정합니다. 이미 등록한 행사의 주최 이름은 등록 당시 이름 그대로입니다.")
    public GlobalResponse<MyOrganizationResponseDto> updateMyOrganization(
            @Valid @RequestBody MyOrganizationUpdateRequestDto request) {
        return organizationService.updateMyOrganization(request);
    }
}
