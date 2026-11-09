package dev.hidly.nubi.review.service;

import dev.hidly.nubi.application.data.entity.Application;
import dev.hidly.nubi.application.data.enums.ApplicationStatus;
import dev.hidly.nubi.application.data.exception.ApplicationAccessDeniedException;
import dev.hidly.nubi.application.data.exception.ApplicationNotFoundException;
import dev.hidly.nubi.application.data.repository.ApplicationRepository;
import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.common.security.SecurityUtil;
import dev.hidly.nubi.notification.data.enums.NotificationTargetType;
import dev.hidly.nubi.notification.data.enums.NotificationType;
import dev.hidly.nubi.notification.service.NotificationService;
import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.entity.ProgramCondition;
import dev.hidly.nubi.program.data.enums.ConditionSource;
import dev.hidly.nubi.program.data.enums.ConditionStatus;
import dev.hidly.nubi.program.data.repository.ProgramConditionRepository;
import dev.hidly.nubi.review.data.dto.request.ReviewCreateRequestDto;
import dev.hidly.nubi.review.data.dto.response.ReviewResponseDto;
import dev.hidly.nubi.review.data.entity.Review;
import dev.hidly.nubi.review.data.entity.ReviewCheck;
import dev.hidly.nubi.review.data.exception.InvalidReviewRequestException;
import dev.hidly.nubi.review.data.exception.ReviewDuplicatedException;
import dev.hidly.nubi.review.data.exception.ReviewNotAllowedException;
import dev.hidly.nubi.review.data.repository.ReviewRepository;
import dev.hidly.nubi.support.data.entity.SupportTag;
import dev.hidly.nubi.support.data.exception.SupportTagNotFoundException;
import dev.hidly.nubi.support.data.repository.SupportTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {
    private static final Set<ApplicationStatus> REVIEWABLE = Set.of(ApplicationStatus.CONFIRMED, ApplicationStatus.ATTENDED);
    private static final String REVIEW_EVIDENCE = "참여 기관 후기로 확인";
    private static final int NOTIFICATION_BODY_LIMIT = 200;

    private final ApplicationRepository applicationRepository;
    private final ReviewRepository reviewRepository;
    private final SupportTagRepository supportTagRepository;
    private final ProgramConditionRepository programConditionRepository;
    private final NotificationService notificationService;

    @Transactional
    public GlobalResponse<ReviewResponseDto> create(Long applicationId, ReviewCreateRequestDto request) {
        Application application = applicationRepository.findWithProgramById(applicationId)
                .orElseThrow(ApplicationNotFoundException::new);
        if (!application.getApplicantOrganization().getId().equals(SecurityUtil.getOrganizationId())) {
            throw new ApplicationAccessDeniedException();
        }
        Program program = application.getProgram();
        if (!REVIEWABLE.contains(application.getStatus())
                || (program.getStartAt() != null && program.getStartAt().isAfter(LocalDateTime.now()))) {
            throw new ReviewNotAllowedException();
        }
        if (reviewRepository.existsByApplicationId(applicationId)) {
            throw new ReviewDuplicatedException();
        }
        Map<Long, SupportTag> tags = findSupportTags(request.checksOrEmpty());

        Review review = Review.builder()
                .application(application)
                .rating(request.getRating())
                .content(trimToNull(request.getContent()))
                .mismatchNote(trimToNull(request.getMismatchNote()))
                .isPublic(request.isPublicOrDefault())
                .reactions(request.reactionsOrEmpty())
                .build();
        request.checksOrEmpty().forEach(check -> review.addCheck(ReviewCheck.builder()
                .supportTag(tags.get(check.getSupportTagId()))
                .answer(check.getAnswer())
                .build()));
        reviewRepository.save(review);
        application.markAttended();
        reflectToConditions(program, request.checksOrEmpty(), tags);

        if (program.getHostOrganization() != null) {
            notificationService.notify(program.getHostOrganization().getId(), NotificationType.NEW_REVIEW,
                    "새 후기가 올라왔어요",
                    body("%s에 %s이(가) 후기를 남겼어요.".formatted(program.getTitle(),
                            application.getApplicantOrganization().getName())),
                    NotificationTargetType.REVIEW, review.getId());
        }
        log.info("[createReview] 후기 작성: applicationId={}, programId={}, reviewId={}, 별점 {}, 확인 항목 {}개",
                applicationId, program.getId(), review.getId(), review.getRating(), review.getChecks().size());

        return GlobalResponse.ok(ReviewResponseDto.from(review));
    }

    // 그랬어요·아니었어요는 프로그램 조건(source REVIEW)에 남겨 다음 기관의 참여 판정에 쓴다. 몰라요는 건드리지 않는다.
    private void reflectToConditions(Program program, List<ReviewCreateRequestDto.Check> checks, Map<Long, SupportTag> tags) {
        Map<Long, ProgramCondition> existing = programConditionRepository
                .findAllByProgramIdAndSource(program.getId(), ConditionSource.REVIEW).stream()
                .collect(Collectors.toMap(condition -> condition.getSupportTag().getId(), Function.identity(), (a, b) -> a));
        for (ReviewCreateRequestDto.Check check : checks) {
            ConditionStatus status = check.getAnswer().toConditionStatus();
            if (status == null) {
                continue;
            }
            ProgramCondition condition = existing.get(check.getSupportTagId());
            if (condition != null) {
                condition.changeStatus(status, REVIEW_EVIDENCE);
            } else {
                programConditionRepository.save(ProgramCondition.builder()
                        .program(program)
                        .supportTag(tags.get(check.getSupportTagId()))
                        .status(status)
                        .evidence(REVIEW_EVIDENCE)
                        .source(ConditionSource.REVIEW)
                        .build());
            }
        }
    }

    private Map<Long, SupportTag> findSupportTags(List<ReviewCreateRequestDto.Check> checks) {
        if (checks.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = checks.stream().map(ReviewCreateRequestDto.Check::getSupportTagId).toList();
        if (Set.copyOf(ids).size() != ids.size()) {
            throw new InvalidReviewRequestException();
        }
        Map<Long, SupportTag> tags = supportTagRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(SupportTag::getId, Function.identity()));
        if (tags.size() != ids.size()) {
            throw new SupportTagNotFoundException();
        }
        return tags;
    }

    private static String body(String text) {
        return text.length() <= NOTIFICATION_BODY_LIMIT ? text : text.substring(0, NOTIFICATION_BODY_LIMIT - 1) + "…";
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
