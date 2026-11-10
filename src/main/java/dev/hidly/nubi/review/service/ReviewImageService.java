package dev.hidly.nubi.review.service;

import dev.hidly.nubi.common.file.FileStorageService;
import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.common.security.SecurityUtil;
import dev.hidly.nubi.review.data.dto.response.ReviewImageResponseDto;
import dev.hidly.nubi.review.data.entity.Review;
import dev.hidly.nubi.review.data.entity.ReviewImage;
import dev.hidly.nubi.review.data.exception.ReviewAccessDeniedException;
import dev.hidly.nubi.review.data.exception.ReviewImageCountExceededException;
import dev.hidly.nubi.review.data.exception.ReviewNotFoundException;
import dev.hidly.nubi.review.data.repository.ReviewImageRepository;
import dev.hidly.nubi.review.data.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewImageService {
    private static final int MAX_IMAGES = 10;

    private final ReviewRepository reviewRepository;
    private final ReviewImageRepository reviewImageRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public GlobalResponse<List<ReviewImageResponseDto>> upload(Long reviewId, List<MultipartFile> images) {
        Review review = reviewRepository.findLockedById(reviewId).orElseThrow(ReviewNotFoundException::new);
        if (!review.getAuthorOrganization().getId().equals(SecurityUtil.getOrganizationId())) {
            throw new ReviewAccessDeniedException();
        }
        List<ReviewImage> current = reviewImageRepository.findAllByReviewIdOrderBySortOrderAsc(reviewId);
        if (images == null || images.isEmpty() || current.size() + images.size() > MAX_IMAGES) {
            throw new ReviewImageCountExceededException();
        }

        int nextOrder = current.isEmpty() ? 0 : current.getLast().getSortOrder() + 1;
        List<String> savedUrls = new ArrayList<>();
        try {
            for (MultipartFile image : images) {
                String url = fileStorageService.save(image);
                savedUrls.add(url);
                current.add(reviewImageRepository.save(ReviewImage.builder()
                        .review(review)
                        .url(url)
                        .sortOrder(nextOrder++)
                        .build()));
            }
        } catch (RuntimeException e) {
            savedUrls.forEach(fileStorageService::delete);
            throw e;
        }

        return GlobalResponse.ok(current.stream().map(ReviewImageResponseDto::from).toList());
    }
}
