package dev.hidly.nubi.review.controller;

import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.review.data.dto.request.ReviewRequestDto;
import dev.hidly.nubi.review.data.dto.response.ReviewImageResponseDto;
import dev.hidly.nubi.review.data.dto.response.ReviewResponseDto;
import dev.hidly.nubi.review.service.ReviewImageService;
import dev.hidly.nubi.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "후기", description = "후기 쓰기, 목록, 답글")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;
    private final ReviewImageService reviewImageService;

    @PostMapping("/applications/{applicationId}/review")
    @Operation(summary = "후기 쓰기", description = "우리 기관 신청에 후기를 남깁니다. 참여 확정(CONFIRMED) 또는 다녀왔어요(ATTENDED) 상태이고 "
            + "행사가 시작된 신청만 쓸 수 있고, 신청 하나에 후기 하나입니다. 쓰면 신청은 다녀왔어요(ATTENDED)가 됩니다. "
            + "checks는 지원 항목별로 공고에 적힌 대로였는지(YES 그랬어요, NO 아니었어요, UNKNOWN 몰라요)이고, "
            + "YES·NO는 프로그램 조건(source REVIEW)에 남아 다음 기관의 참여 판정에 쓰입니다. "
            + "reactions는 ENJOYED, WANT_AGAIN, HAD_DIFFICULTY, SHORT_BREAKS, LONG_DISTANCE. isPublic을 비우면 공개입니다. "
            + "주최 기관에 새 후기 알림이 갑니다.")
    public GlobalResponse<ReviewResponseDto> create(@PathVariable Long applicationId,
                                                    @Valid @RequestBody ReviewRequestDto request) {
        return reviewService.create(applicationId, request);
    }

    @PutMapping("/reviews/{reviewId}")
    @Operation(summary = "후기 수정", description = "후기를 쓴 기관만 고칠 수 있습니다. 후기 쓰기와 같은 내용을 통째로 다시 보냅니다"
            + "(보내지 않은 확인 항목·반응은 지워지고, isPublic을 비우면 공개). "
            + "바뀐 YES·NO는 프로그램 조건(source REVIEW)에 다시 반영됩니다. 사진은 그대로 둡니다.")
    public GlobalResponse<ReviewResponseDto> update(@PathVariable Long reviewId,
                                                    @Valid @RequestBody ReviewRequestDto request) {
        return reviewService.update(reviewId, request);
    }

    @PostMapping(value = "/reviews/{reviewId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "후기 사진 올리기", description = "후기를 쓴 기관만 올릴 수 있고, 후기당 10장까지 저장합니다. "
            + "JPG, PNG, WEBP만, 한 장에 5MB까지입니다. 응답은 그 후기의 사진 전체(올린 순서)입니다.")
    public GlobalResponse<List<ReviewImageResponseDto>> uploadImages(@PathVariable Long reviewId,
                                                                     @RequestPart("images") List<MultipartFile> images) {
        return reviewImageService.upload(reviewId, images);
    }
}
