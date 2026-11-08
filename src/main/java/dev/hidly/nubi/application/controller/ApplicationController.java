package dev.hidly.nubi.application.controller;

import dev.hidly.nubi.application.data.dto.request.DirectApplicationRequestDto;
import dev.hidly.nubi.application.data.dto.response.ApplicationResponseDto;
import dev.hidly.nubi.application.service.ApplicationService;
import dev.hidly.nubi.common.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "신청", description = "바로 신청, 내 신청, 문의")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ApplicationController {
    private final ApplicationService applicationService;

    @PostMapping("/programs/{programId}/applications")
    @Operation(summary = "바로 신청", description = "공개 중인 기관 행사(신청 방법 DIRECT)에 그룹으로 신청합니다. 그룹의 공통 지원과 고른 개별 케어를 그대로 복사해 두고, 접수 기다림(SUBMITTED)으로 시작합니다. participantCount를 비우면 그룹 인원, 정원은 이용자 수로만 셉니다.")
    public GlobalResponse<ApplicationResponseDto> applyDirect(@PathVariable Long programId,
                                                              @Valid @RequestBody DirectApplicationRequestDto request) {
        return applicationService.applyDirect(programId, request);
    }
}
