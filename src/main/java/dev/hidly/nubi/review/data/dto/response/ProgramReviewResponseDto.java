package dev.hidly.nubi.review.data.dto.response;

import dev.hidly.nubi.review.data.dto.response.ReviewResponseDto.CheckItem;
import dev.hidly.nubi.review.data.entity.Review;
import dev.hidly.nubi.review.data.entity.ReviewReply;
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
        Reply reply,
        LocalDateTime createdAt
) {
    // 주최 기관의 답글. 없으면 reply가 null
    public record Reply(String organizationName, String content, LocalDateTime createdAt) {
        public static Reply from(ReviewReply reply) {
            return reply == null ? null
                    : new Reply(reply.getOrganization().getName(), reply.getContent(), reply.getCreatedAt());
        }
    }

    public static ProgramReviewResponseDto of(Review review, List<String> imageUrls, ReviewReply reply) {
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
                Reply.from(reply),
                review.getCreatedAt());
    }
}
