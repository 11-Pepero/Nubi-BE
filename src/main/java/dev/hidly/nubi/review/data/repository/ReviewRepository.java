package dev.hidly.nubi.review.data.repository;

import dev.hidly.nubi.review.data.entity.Review;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsByApplicationId(Long applicationId);

    @EntityGraph(attributePaths = {"application", "authorOrganization"})
    Page<Review> findAllByProgramIdAndIsPublicTrue(Long programId, Pageable pageable);

    // 사진을 동시에 올려도 장수 제한을 넘지 않게 후기 행을 잠근다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Review r where r.id = :id")
    Optional<Review> findLockedById(@Param("id") Long id);
}
