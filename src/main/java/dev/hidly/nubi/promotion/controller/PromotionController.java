package dev.hidly.nubi.promotion.controller;

import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.promotion.data.dto.response.PromotionResponseDto;
import dev.hidly.nubi.promotion.service.PromotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "행사 관리", description = "행사 등록, 수정, 사진, 신청 기관 관리")
@RestController
@RequestMapping("/api/v1/events/{eventId}/promotions")
@RequiredArgsConstructor
public class PromotionController {
    private final PromotionService promotionService;

    @PostMapping
    @Operation(summary = "AI 홍보문 쓰기", description = "주최 기관만 쓸 수 있습니다. 행사 이름, 날짜, 장소, 정원, 소개, 주최가 제공한다고 체크한 지원, "
            + "장소의 확인된 편의시설로 general(일반), easy(쉬운 말, 발달장애인용), sms(문자·포스터용 짧은 문구)를 씁니다. "
            + "쉬운 말은 한 줄 35자, 15줄 이내이고 어려운 낱말(접근성, 편의시설 등)을 쓰지 않으며, 어기면 초안을 씁니다. "
            + "저장하지 않으니 다시 쓰기는 다시 부르면 됩니다. aiWritten이 false면 AI 없이 쓴 초안입니다.")
    public GlobalResponse<PromotionResponseDto> write(@PathVariable Long eventId) {
        return promotionService.write(eventId);
    }
}
