package dev.hidly.nubi.review.data.repository;

import dev.hidly.nubi.review.data.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsByApplicationId(Long applicationId);
}
