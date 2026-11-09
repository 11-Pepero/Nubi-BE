package dev.hidly.nubi.review.controller;

import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.common.response.PageResponse;
import dev.hidly.nubi.review.data.dto.request.ReviewReplyRequestDto;
import dev.hidly.nubi.review.data.dto.request.ReviewRequestDto;
import dev.hidly.nubi.review.data.dto.response.ReviewReplyResponseDto;
import dev.hidly.nubi.review.data.dto.response.ProgramReviewResponseDto;
import dev.hidly.nubi.review.data.dto.response.ReceivedReviewsResponseDto;
import dev.hidly.nubi.review.data.enums.ReviewSort;
import dev.hidly.nubi.review.data.dto.response.ReviewImageResponseDto;
import dev.hidly.nubi.review.data.dto.response.ReviewResponseDto;
import dev.hidly.nubi.review.service.ReviewImageService;
import dev.hidly.nubi.review.service.ReviewReplyService;
import dev.hidly.nubi.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final ReviewReplyService reviewReplyService;

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

    @GetMapping("/programs/{programId}/reviews")
    @Operation(summary = "프로그램 후기 목록", description = "다른 기관에도 공개한 후기(isPublic = true)만 최근 순으로 보여 줍니다. "
            + "작성 기관 이름, 별점, 다녀온 인원(이용자·동반), 지원 항목별 확인 결과, 반응, 내용, 사진을 줍니다. "
            + "비공개 후기는 주최 기관의 받은 후기 목록에서만 보입니다.")
    public GlobalResponse<PageResponse<ProgramReviewResponseDto>> getProgramReviews(
            @PathVariable Long programId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return reviewService.getProgramReviews(programId, page, size);
    }

    @GetMapping("/events/{eventId}/reviews")
    @Operation(summary = "받은 후기 목록", description = "주최 기관만 볼 수 있고, 비공개 후기도 함께 보여 줍니다. "
            + "stats는 참여 기관 수·이용자 수·동반인 수(확정 또는 다녀온 신청 기준), 받은 후기 수, 평균 별점(소수 첫째 자리, 후기가 없으면 null), 답글 기다림 수입니다. "
            + "checks는 지원 항목별 그랬어요·아니었어요·몰라요 수, reactions는 반응별 수입니다. "
            + "sort는 LATEST(최신순, 기본) 또는 RATING_ASC(별점 낮은 순), photoOnly=true면 사진 있는 후기만 목록에 담습니다.")
    public GlobalResponse<ReceivedReviewsResponseDto> getReceivedReviews(
            @PathVariable Long eventId,
            @RequestParam(defaultValue = "LATEST") ReviewSort sort,
            @RequestParam(defaultValue = "false") boolean photoOnly,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return reviewService.getReceivedReviews(eventId, sort, photoOnly, page, size);
    }

    @PostMapping("/reviews/{reviewId}/reply")
    @Operation(summary = "후기 답글", description = "행사를 연 주최 기관만 후기 하나에 답글 하나를 남길 수 있습니다(두 번째는 409). "
            + "답글은 프로그램 후기 목록과 받은 후기 목록의 reply에 보입니다.")
    public GlobalResponse<ReviewReplyResponseDto> reply(@PathVariable Long reviewId,
                                                        @Valid @RequestBody ReviewReplyRequestDto request) {
        return reviewReplyService.reply(reviewId, request);
    }

    @PostMapping(value = "/reviews/{reviewId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "후기 사진 올리기", description = "후기를 쓴 기관만 올릴 수 있고, 후기당 10장까지 저장합니다. "
            + "JPG, PNG, WEBP만, 한 장에 5MB까지입니다. 응답은 그 후기의 사진 전체(올린 순서)입니다.")
    public GlobalResponse<List<ReviewImageResponseDto>> uploadImages(@PathVariable Long reviewId,
                                                                     @RequestPart("images") List<MultipartFile> images) {
        return reviewImageService.upload(reviewId, images);
    }
}
