package dev.hidly.nubi.promotion.controller;

import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.promotion.data.dto.request.PosterRequestDto;
import dev.hidly.nubi.promotion.data.dto.response.PromotionResponseDto;
import dev.hidly.nubi.promotion.service.PromotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

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

    @PostMapping("/poster")
    @Operation(summary = "포스터 PDF 내려받기", description = "주최 기관만 내려받을 수 있는 A4 포스터 PDF입니다(포스터로 받기). "
            + "대표 사진(행사 사진 중 첫 번째, JPG·PNG), 행사 이름, 일시·장소·정원, 쉬운 말 홍보문, 주최를 한 장에 넣습니다. "
            + "easy에 화면에서 보고 고친 쉬운 말 홍보문을 보내면 그대로 넣고, 비우면 AI 없이 쓴 쉬운 말 초안을 넣습니다. AI는 다시 부르지 않습니다.")
    public ResponseEntity<byte[]> poster(@PathVariable Long eventId,
                                         @Valid @RequestBody(required = false) PosterRequestDto request) {
        PromotionService.PosterFile file = promotionService.poster(eventId, request == null ? null : request.getEasy());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.content());
    }
}
