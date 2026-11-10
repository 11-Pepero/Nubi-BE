package dev.hidly.nubi.document.controller;

import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.document.data.dto.response.ApplicationDocumentsResponseDto;
import dev.hidly.nubi.document.service.ApplicationDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "신청", description = "바로 신청, 내 신청, 문의")
@RestController
@RequestMapping("/api/v1/applications/{applicationId}/documents")
@RequiredArgsConstructor
public class ApplicationDocumentController {
    private final ApplicationDocumentService applicationDocumentService;

    @PostMapping
    @Operation(summary = "AI 신청서 쓰기", description = "우리 기관 신청으로 프로그램 참가 신청서(FORM), 문의 문자(INQUIRY), "
            + "통화 대본(CALL_SCRIPT)을 씁니다. 신청할 때 복사해 둔 지원 항목을 공고 조건과 견줘 confirmed(공고에 있어요)와 "
            + "toAsk(물어봐야 해요)로 나누고, 문의 문자와 통화 대본에는 toAsk만 담습니다. 다시 부르면 서류를 새로 씁니다(다시 쓰기). "
            + "취소·거절된 신청은 400입니다.")
    public GlobalResponse<ApplicationDocumentsResponseDto> write(@PathVariable Long applicationId) {
        return applicationDocumentService.write(applicationId);
    }
}
