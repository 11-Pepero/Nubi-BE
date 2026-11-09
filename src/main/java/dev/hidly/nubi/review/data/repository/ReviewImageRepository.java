package dev.hidly.nubi.review.data.repository;

import dev.hidly.nubi.review.data.entity.ReviewImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewImageRepository extends JpaRepository<ReviewImage, Long> {
    List<ReviewImage> findAllByReviewIdOrderBySortOrderAsc(Long reviewId);
}
