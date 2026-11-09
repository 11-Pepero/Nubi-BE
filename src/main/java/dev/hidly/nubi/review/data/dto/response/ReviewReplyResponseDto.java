package dev.hidly.nubi.review.data.dto.response;

import dev.hidly.nubi.review.data.entity.ReviewReply;

import java.time.LocalDateTime;

public record ReviewReplyResponseDto(Long replyId, Long reviewId, String organizationName, String content,
                                     LocalDateTime createdAt) {
    public static ReviewReplyResponseDto from(ReviewReply reply) {
        return new ReviewReplyResponseDto(reply.getId(), reply.getReview().getId(), reply.getOrganization().getName(),
                reply.getContent(), reply.getCreatedAt());
    }
}
