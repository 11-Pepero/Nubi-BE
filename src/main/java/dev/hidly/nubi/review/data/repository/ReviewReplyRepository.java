package dev.hidly.nubi.review.data.repository;

import dev.hidly.nubi.review.data.entity.ReviewReply;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReviewReplyRepository extends JpaRepository<ReviewReply, Long> {
    boolean existsByReviewId(Long reviewId);

    @EntityGraph(attributePaths = "organization")
    List<ReviewReply> findAllByReviewIdIn(Collection<Long> reviewIds);
}
