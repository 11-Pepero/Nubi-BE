package dev.hidly.nubi.review.data.dto.response;

import dev.hidly.nubi.review.data.dto.response.ReviewResponseDto.CheckItem;
import dev.hidly.nubi.review.data.entity.Review;
import dev.hidly.nubi.review.data.enums.ReviewReaction;

import java.time.LocalDateTime;
import java.util.List;

// 다른 기관에게 보이는 후기 (화면 15 오른쪽 미리보기와 같은 내용)
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
                review.getCreatedAt());
    }
}
