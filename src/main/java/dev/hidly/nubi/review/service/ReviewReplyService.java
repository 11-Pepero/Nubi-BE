package dev.hidly.nubi.review.service;

import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.common.security.SecurityUtil;
import dev.hidly.nubi.organization.data.repository.OrganizationRepository;
import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.exception.ProgramAccessDeniedException;
import dev.hidly.nubi.program.data.exception.ProgramNotFoundException;
import dev.hidly.nubi.review.data.dto.request.ReviewReplyRequestDto;
import dev.hidly.nubi.review.data.dto.response.ReviewReplyResponseDto;
import dev.hidly.nubi.review.data.entity.Review;
import dev.hidly.nubi.review.data.entity.ReviewReply;
import dev.hidly.nubi.review.data.exception.ReviewNotFoundException;
import dev.hidly.nubi.review.data.exception.ReviewReplyDuplicatedException;
import dev.hidly.nubi.review.data.repository.ReviewReplyRepository;
import dev.hidly.nubi.review.data.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewReplyService {
    private final ReviewRepository reviewRepository;
    private final ReviewReplyRepository reviewReplyRepository;
    private final OrganizationRepository organizationRepository;

    // 주최 기관만 후기 하나에 답글 하나를 남긴다. 후기 행을 잠가 답글이 두 번 들어가지 않게 한다.
    @Transactional
    public GlobalResponse<ReviewReplyResponseDto> reply(Long reviewId, ReviewReplyRequestDto request) {
        Review review = reviewRepository.findLockedById(reviewId).orElseThrow(ReviewNotFoundException::new);
        Program program = review.getProgram();
        if (program.isDeleted()) {
            throw new ProgramNotFoundException();
        }
        Long organizationId = SecurityUtil.getOrganizationId();
        if (!program.isHostedBy(organizationId)) {
            throw new ProgramAccessDeniedException();
        }
        if (reviewReplyRepository.existsByReviewId(reviewId)) {
            throw new ReviewReplyDuplicatedException();
        }

        ReviewReply reply = reviewReplyRepository.save(ReviewReply.builder()
                .review(review)
                .organization(organizationRepository.getReferenceById(organizationId))
                .content(request.getContent().trim())
                .build());
        log.info("[replyReview] 후기 답글: reviewId={}, programId={}, organizationId={}", reviewId, program.getId(), organizationId);

        return GlobalResponse.ok(ReviewReplyResponseDto.from(reply));
    }
}
