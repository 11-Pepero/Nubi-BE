package dev.hidly.nubi.review.data.repository;

import dev.hidly.nubi.application.data.enums.ApplicationStatus;
import dev.hidly.nubi.review.data.entity.Review;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsByApplicationId(Long applicationId);

    @EntityGraph(attributePaths = {"application", "authorOrganization"})
    Page<Review> findAllByProgramIdAndIsPublicTrue(Long programId, Pageable pageable);

    // 주최 기관의 받은 후기: 공개·비공개 모두. photoOnly면 사진 있는 후기만.
    @EntityGraph(attributePaths = {"application", "authorOrganization"})
    @Query("""
            select r from Review r
            where r.program.id = :programId
              and (:photoOnly = false or exists (select 1 from ReviewImage i where i.review = r))
            """)
    Page<Review> findReceived(@Param("programId") Long programId, @Param("photoOnly") boolean photoOnly, Pageable pageable);

    // [후기 수, 평균 별점]
    @Query("select count(r), avg(r.rating) from Review r where r.program.id = :programId")
    List<Object[]> countAndAverageRating(@Param("programId") Long programId);

    // 지원 항목별 [지원 항목 ID, 코드, 이름, 답, 수]
    @Query("""
            select c.supportTag.id, c.supportTag.code, c.supportTag.label, c.answer, count(c)
            from ReviewCheck c
            where c.review.program.id = :programId
            group by c.supportTag.id, c.supportTag.code, c.supportTag.label, c.answer
            order by c.supportTag.id
            """)
    List<Object[]> countChecks(@Param("programId") Long programId);

    // 반응별 [반응, 수]
    @Query("select x, count(r) from Review r join r.reactions x where r.program.id = :programId group by x")
    List<Object[]> countReactions(@Param("programId") Long programId);

    // 받은 후기 화면의 참여 기관·인원: 확정되었거나 다녀온 신청 기준 [기관 수, 이용자 수, 동반인 수]
    @Query("""
            select count(distinct a.applicantOrganization.id), coalesce(sum(a.participantCount), 0), coalesce(sum(a.companionCount), 0)
            from Application a
            where a.program.id = :programId and a.status in :statuses
            """)
    List<Object[]> sumParticipants(@Param("programId") Long programId, @Param("statuses") Collection<ApplicationStatus> statuses);

    // 사진을 동시에 올려도 장수 제한을 넘지 않게 후기 행을 잠근다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Review r where r.id = :id")
    Optional<Review> findLockedById(@Param("id") Long id);
}
