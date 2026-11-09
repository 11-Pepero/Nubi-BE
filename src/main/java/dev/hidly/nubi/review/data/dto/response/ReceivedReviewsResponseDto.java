package dev.hidly.nubi.review.data.dto.response;

import dev.hidly.nubi.common.response.PageResponse;
import dev.hidly.nubi.review.data.enums.ReviewReaction;

import java.util.List;

// 받은 후기 (화면 16): 위쪽 숫자, 공고에 적힌 대로였나요? 집계, 반응 수, 후기 목록
public record ReceivedReviewsResponseDto(
        Stats stats,
        List<CheckStat> checks,
        List<ReactionStat> reactions,
        PageResponse<ProgramReviewResponseDto> reviews
) {
    // averageRating은 후기가 없으면 null
    public record Stats(long organizationCount, long participantCount, long companionCount,
                        long reviewCount, Double averageRating, long awaitingReplyCount) {
    }

    public record CheckStat(Long supportTagId, String code, String label, long yesCount, long noCount, long unknownCount) {
    }

    public record ReactionStat(ReviewReaction reaction, long count) {
    }
}
