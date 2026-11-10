package dev.hidly.nubi.review.data.dto.response;

import dev.hidly.nubi.review.data.entity.Review;
import dev.hidly.nubi.review.data.entity.ReviewCheck;
import dev.hidly.nubi.review.data.enums.CheckAnswer;
import dev.hidly.nubi.review.data.enums.ReviewReaction;

import java.time.LocalDateTime;
import java.util.List;

public record ReviewResponseDto(
        Long reviewId,
        Long applicationId,
        Long programId,
        String programTitle,
        int rating,
        List<CheckItem> checks,
        String mismatchNote,
        List<ReviewReaction> reactions,
        String content,
        boolean isPublic,
        LocalDateTime createdAt
) {
    public record CheckItem(Long supportTagId, String code, String label, CheckAnswer answer) {
        public static CheckItem from(ReviewCheck check) {
            return new CheckItem(check.getSupportTag().getId(), check.getSupportTag().getCode(),
                    check.getSupportTag().getLabel(), check.getAnswer());
        }
    }

    public static ReviewResponseDto from(Review review) {
        return new ReviewResponseDto(
                review.getId(),
                review.getApplication().getId(),
                review.getProgram().getId(),
                review.getProgram().getTitle(),
                review.getRating(),
                review.getChecks().stream().map(CheckItem::from).toList(),
                review.getMismatchNote(),
                List.copyOf(review.getReactions()),
                review.getContent(),
                review.isPublic(),
                review.getCreatedAt());
    }
}
