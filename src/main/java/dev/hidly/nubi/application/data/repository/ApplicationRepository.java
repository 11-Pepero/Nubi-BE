package dev.hidly.nubi.application.data.repository;

import dev.hidly.nubi.application.data.entity.Application;
import dev.hidly.nubi.application.data.enums.ApplicationStatus;
import dev.hidly.nubi.organization.data.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    boolean existsByProgramIdAndGroupIdAndStatusIn(Long programId, Long groupId, Collection<ApplicationStatus> statuses);

    @Query("""
            select coalesce(sum(a.participantCount), 0) from Application a
            where a.program.id = :programId and a.status not in :released
            """)
    long sumParticipantsExcept(@Param("programId") Long programId,
                               @Param("released") Collection<ApplicationStatus> released);

    @Query("""
            select coalesce(sum(a.participantCount), 0) from Application a
            where a.program.id = :programId and a.status not in :released and a.id <> :applicationId
            """)
    long sumParticipantsExceptApplication(@Param("programId") Long programId,
                                          @Param("released") Collection<ApplicationStatus> released,
                                          @Param("applicationId") Long applicationId);

    @Query("""
            select a.program.id, coalesce(sum(a.participantCount), 0) from Application a
            where a.program.id in :programIds and a.status not in :released
            group by a.program.id
            """)
    List<Object[]> sumParticipantsByProgramIds(@Param("programIds") Collection<Long> programIds,
                                               @Param("released") Collection<ApplicationStatus> released);

    @Query("""
            select count(a) from Application a
            where a.applicantOrganization.id = :organizationId and a.status = :status and a.program.startAt >= :from
            """)
    long countByApplicantAndStatusStartingAfter(@Param("organizationId") Long organizationId,
                                                @Param("status") ApplicationStatus status,
                                                @Param("from") LocalDateTime from);

    @EntityGraph(attributePaths = {"applicantOrganization", "group"})
    Page<Application> findByProgramId(Long programId, Pageable pageable);

    @EntityGraph(attributePaths = {"applicantOrganization", "group"})
    Page<Application> findByProgramIdAndStatusIn(Long programId, Collection<ApplicationStatus> statuses, Pageable pageable);

    @EntityGraph(attributePaths = {"program", "applicantOrganization", "group"})
    Optional<Application> findWithProgramById(Long id);

    @Query("""
            select new dev.hidly.nubi.application.data.repository.EventApplicationStats(
                a.program.id,
                count(distinct a.applicantOrganization.id),
                coalesce(sum(a.participantCount), 0),
                coalesce(sum(a.companionCount), 0),
                sum(case when a.status = dev.hidly.nubi.application.data.enums.ApplicationStatus.SUBMITTED then 1L else 0L end))
            from Application a
            where a.program.id in :programIds and a.status not in :released
            group by a.program.id
            """)
    List<EventApplicationStats> statsByProgramIds(@Param("programIds") Collection<Long> programIds,
                                                  @Param("released") Collection<ApplicationStatus> released);

    @Query("select a.status, count(a) from Application a where a.program.id = :programId group by a.status")
    List<Object[]> countByStatus(@Param("programId") Long programId);

    @EntityGraph(attributePaths = {"program", "program.venue", "program.hostOrganization", "group"})
    Page<Application> findByApplicantOrganizationId(Long organizationId, Pageable pageable);

    @EntityGraph(attributePaths = {"program", "program.venue", "program.hostOrganization", "group"})
    Page<Application> findByApplicantOrganizationIdAndStatusIn(Long organizationId, Collection<ApplicationStatus> statuses,
                                                               Pageable pageable);

    @Query("select a.status, count(a) from Application a where a.applicantOrganization.id = :organizationId group by a.status")
    List<Object[]> countByStatusOfApplicant(@Param("organizationId") Long organizationId);

    @Query("""
            select count(a) from Application a
            where a.applicantOrganization.id = :organizationId and a.status = :status
              and a.program.startAt >= :from and a.program.startAt < :to
            """)
    long countByApplicantAndStatusStartingBetween(@Param("organizationId") Long organizationId,
                                                  @Param("status") ApplicationStatus status,
                                                  @Param("from") LocalDateTime from,
                                                  @Param("to") LocalDateTime to);

    // 행사가 끝나면(끝나는 시각, 없으면 시작 시각이 지나면) 확정 신청을 다녀왔어요로 바꾼다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Application a set a.status = dev.hidly.nubi.application.data.enums.ApplicationStatus.ATTENDED
            where a.status = dev.hidly.nubi.application.data.enums.ApplicationStatus.CONFIRMED
              and a.program.id in (
                select p.id from Program p where coalesce(p.endAt, p.startAt) < :now
              )
            """)
    int markAttendedFinished(@Param("now") LocalDateTime now);

    // 행사 관리 요약(API-40): 내가 주최한 행사들에 들어온 신청
    @Query("""
            select count(a) from Application a
            where a.program.hostOrganization.id = :organizationId and a.program.deletedAt is null and a.status = :status
            """)
    long countForHostByStatus(@Param("organizationId") Long organizationId, @Param("status") ApplicationStatus status);

    @Query("""
            select coalesce(sum(a.participantCount), 0), coalesce(sum(a.companionCount), 0) from Application a
            where a.program.hostOrganization.id = :organizationId and a.program.deletedAt is null
              and a.status = :status and a.program.startAt >= :from and a.program.startAt < :to
            """)
    List<Object[]> sumPeopleForHostStartingBetween(@Param("organizationId") Long organizationId,
                                                   @Param("status") ApplicationStatus status,
                                                   @Param("from") LocalDateTime from,
                                                   @Param("to") LocalDateTime to);

    // 필요 지원 모아보기(API-48): 거절·취소를 뺀 신청을 먼저 들어온 순으로
    @EntityGraph(attributePaths = {"applicantOrganization", "group"})
    List<Application> findByProgramIdAndStatusNotInOrderByIdAsc(Long programId, Collection<ApplicationStatus> statuses);

    // 신청 명단 내려받기(API-49): 고른 상태의 신청을 먼저 들어온 순으로
    @EntityGraph(attributePaths = {"applicantOrganization", "group"})
    List<Application> findByProgramIdAndStatusInOrderByIdAsc(Long programId, Collection<ApplicationStatus> statuses);

    // 신청에는 담당자가 따로 저장되지 않아 기관의 첫 담당자를 연락처로 쓴다.
    @Query("select m from Member m where m.organization.id in :organizationIds order by m.id")
    List<Member> findMembersOfOrganizations(@Param("organizationIds") Collection<Long> organizationIds);

    // 마감 알림용: 모집 중인 프로그램 중 마감이 [from, to)에 있고 아직 작성 중인 신청
    @Query("""
            select a from Application a join fetch a.program p
            where a.status = dev.hidly.nubi.application.data.enums.ApplicationStatus.PREPARING
              and p.status = dev.hidly.nubi.program.data.enums.ProgramStatus.OPEN
              and p.deletedAt is null
              and p.deadlineAt >= :from and p.deadlineAt < :to
            """)
    List<Application> findPreparingByDeadlineBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // ---- 후기 수 (후기 테이블은 C 파트, 여기서는 세기만 한다) ----

    // 내 신청 요약 "후기 n건 남기기": 후기를 쓸 수 있는데 아직 안 쓴 신청 수.
    // 후기 쓰기(API-50)와 같은 조건: 확정·다녀옴, 삭제되지 않은 프로그램, 시작 시각이 지났거나 비어 있음.
    @Query("""
            select count(a) from Application a join a.program p
            where a.applicantOrganization.id = :organizationId and a.status in :statuses
              and p.deletedAt is null and (p.startAt is null or p.startAt <= :now)
              and not exists (select 1 from Review r where r.application = a)
            """)
    long countReviewPending(@Param("organizationId") Long organizationId,
                            @Param("statuses") Collection<ApplicationStatus> statuses,
                            @Param("now") LocalDateTime now);

    // 내 신청 목록 한 줄마다 쓴 후기 ID: [신청 ID, 후기 ID]
    @Query("select r.application.id, r.id from Review r where r.application.id in :applicationIds")
    List<Object[]> findReviewIdsByApplicationIds(@Param("applicationIds") Collection<Long> applicationIds);

    // 행사 관리 요약 "받은 후기": 내가 주최한(삭제되지 않은) 행사에 달린 후기 수. 공개·비공개 모두.
    @Query("""
            select count(r) from Review r
            where r.program.hostOrganization.id = :organizationId and r.program.deletedAt is null
            """)
    long countReviewsForHost(@Param("organizationId") Long organizationId);

    // 내 행사 목록 한 줄마다 [행사 ID, 후기 수, 평균 별점]
    @Query("select r.program.id, count(r), avg(r.rating) from Review r where r.program.id in :programIds group by r.program.id")
    List<Object[]> reviewStatsByProgramIds(@Param("programIds") Collection<Long> programIds);
}
