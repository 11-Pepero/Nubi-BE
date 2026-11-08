package dev.hidly.nubi.application.controller;

import dev.hidly.nubi.application.data.dto.request.DirectApplicationRequestDto;
import dev.hidly.nubi.application.data.dto.response.ApplicationResponseDto;
import dev.hidly.nubi.application.data.dto.response.MyApplicationCountResponseDto;
import dev.hidly.nubi.application.data.dto.response.MyApplicationDetailResponseDto;
import dev.hidly.nubi.application.data.dto.response.MyApplicationSummaryResponseDto;
import dev.hidly.nubi.application.data.enums.ApplicationStatus;
import dev.hidly.nubi.application.service.ApplicationService;
import dev.hidly.nubi.application.service.MyApplicationService;
import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.common.response.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
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
    private final MyApplicationService myApplicationService;

    @GetMapping("/applications")
    @Operation(summary = "내 신청 목록", description = "우리 기관이 낸 신청을 최근 순으로 보여 줍니다. status를 여러 번 보내 탭별로 조회합니다(준비 중 = PREPARING, 접수 기다림 = SUBMITTED, INQUIRY, WAITING, 참여 예정 = CONFIRMED, 다녀왔어요 = ATTENDED). step은 준비 → 접수 → 참여 → 후기 단계 표시용입니다.")
    public GlobalResponse<PageResponse<MyApplicationSummaryResponseDto>> getMyApplications(
            @RequestParam(required = false) List<ApplicationStatus> status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return myApplicationService.getMyApplications(status, page, size);
    }

    @GetMapping("/applications/summary")
    @Operation(summary = "내 신청 요약", description = "준비 중, 접수 기다림, 참여 예정(이번 주 포함), 다녀왔어요 건수와 상태별 건수입니다. total은 거절·취소를 뺀 합계입니다.")
    public GlobalResponse<MyApplicationCountResponseDto> getSummary() {
        return myApplicationService.getSummary();
    }

    @GetMapping("/applications/{applicationId}")
    @Operation(summary = "신청 상세", description = "우리 기관 신청만 볼 수 있습니다. 행사 정보, 신청할 때 복사해 둔 인원·지원·개별 케어, 주고받은 문의를 보여 줍니다.")
    public GlobalResponse<MyApplicationDetailResponseDto> getMyApplication(@PathVariable Long applicationId) {
        return myApplicationService.getMyApplication(applicationId);
    }

    @PostMapping("/programs/{programId}/applications")
    @Operation(summary = "바로 신청", description = "공개 중인 기관 행사(신청 방법 DIRECT)에 그룹으로 신청합니다. 그룹의 공통 지원과 고른 개별 케어를 그대로 복사해 두고, 접수 기다림(SUBMITTED)으로 시작합니다. participantCount를 비우면 그룹 인원, 정원은 이용자 수로만 셉니다.")
    public GlobalResponse<ApplicationResponseDto> applyDirect(@PathVariable Long programId,
                                                              @Valid @RequestBody DirectApplicationRequestDto request) {
        return applicationService.applyDirect(programId, request);
    }
}
