package dev.hidly.nubi.application.controller;

import dev.hidly.nubi.application.data.dto.request.DirectApplicationRequestDto;
import dev.hidly.nubi.application.data.dto.response.ApplicationResponseDto;
import dev.hidly.nubi.application.data.dto.request.CancelRequestDto;
import dev.hidly.nubi.application.data.dto.request.MessageRequestDto;
import dev.hidly.nubi.application.data.dto.request.PrepareApplicationRequestDto;
import dev.hidly.nubi.application.data.dto.request.SubmittedRequestDto;
import dev.hidly.nubi.application.data.dto.response.MyApplicationCountResponseDto;
import dev.hidly.nubi.application.data.dto.response.PreparedApplicationResponseDto;
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

    @PostMapping("/applications/prepare")
    @Operation(summary = "신청 준비 만들기", description = "외부 공고(신청 방법 ONLINE, PHONE, PAPER)에 낼 신청을 준비 중(PREPARING)으로 만듭니다. 앱은 대신 접수하지 않습니다. 그룹 공통 지원과 개별 케어를 복사해 두고, 접수 채널(연락처, 접수 링크, 마감)을 함께 돌려줍니다.")
    public GlobalResponse<PreparedApplicationResponseDto> prepare(@Valid @RequestBody PrepareApplicationRequestDto request) {
        return applicationService.prepare(request);
    }

    @PatchMapping("/applications/{applicationId}/submitted")
    @Operation(summary = "접수 완료 표시", description = "담당자가 전화·온라인·서면으로 직접 접수한 뒤 누릅니다. 준비 중(PREPARING) 신청만 접수 기다림(SUBMITTED)으로 바뀝니다. channel과 submittedAt은 비우면 공고의 접수 방법과 지금 시각입니다.")
    public GlobalResponse<MyApplicationDetailResponseDto> markSubmitted(@PathVariable Long applicationId,
                                                                        @RequestBody(required = false) SubmittedRequestDto request) {
        return applicationService.markSubmitted(applicationId, request == null ? new SubmittedRequestDto() : request);
    }

    @PostMapping("/applications/{applicationId}/messages")
    @Operation(summary = "문의 보내기", description = "우리 기관 신청에 대해 주최 기관에 문의를 남깁니다. 접수 기다림(SUBMITTED) 신청은 문의 있음(INQUIRY)으로 바뀝니다. 취소된 신청에는 보낼 수 없습니다.")
    public GlobalResponse<MyApplicationDetailResponseDto> sendInquiry(@PathVariable Long applicationId,
                                                                      @Valid @RequestBody MessageRequestDto request) {
        return applicationService.sendInquiry(applicationId, request);
    }

    @PatchMapping("/applications/{applicationId}/cancel")
    @Operation(summary = "신청 취소", description = "행사가 시작하기 전까지 취소할 수 있습니다. 거절·취소·다녀옴 상태는 취소할 수 없습니다. reason을 보내면 신청 기관 메시지로 남깁니다.")
    public GlobalResponse<MyApplicationDetailResponseDto> cancel(@PathVariable Long applicationId,
                                                                 @Valid @RequestBody(required = false) CancelRequestDto request) {
        return applicationService.cancel(applicationId, request == null ? new CancelRequestDto() : request);
    }

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
