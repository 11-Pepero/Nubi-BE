package dev.hidly.nubi.review.data.dto.response;

import dev.hidly.nubi.review.data.dto.response.ReviewResponseDto.CheckItem;
import dev.hidly.nubi.review.data.entity.Review;
import dev.hidly.nubi.review.data.enums.ReviewReaction;

import java.time.LocalDateTime;
import java.util.List;

// 후기 목록의 한 줄. 프로그램 후기 목록(공개만)과 받은 후기 목록(주최 기관, 비공개 포함)이 같이 쓴다.
public record ProgramReviewResponseDto(
        Long reviewId,
        String organizationName,
        int rating,
        int participantCount,
        int companionCount,
        List<CheckItem> checks,
        String mismatchNote,
        List<ReviewReaction> reactions,
        String content,
        List<String> imageUrls,
        boolean isPublic,
        LocalDateTime createdAt
) {
    public static ProgramReviewResponseDto of(Review review, List<String> imageUrls) {
        return new ProgramReviewResponseDto(
                review.getId(),
                review.getAuthorOrganization().getName(),
                review.getRating(),
                review.getApplication().getParticipantCount(),
                review.getApplication().getCompanionCount(),
                review.getChecks().stream().map(CheckItem::from).toList(),
                review.getMismatchNote(),
                List.copyOf(review.getReactions()),
                review.getContent(),
                imageUrls,
                review.isPublic(),
                review.getCreatedAt());
    }
}
