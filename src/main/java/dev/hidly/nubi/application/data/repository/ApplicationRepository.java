package dev.hidly.nubi.application.data.repository;

import dev.hidly.nubi.application.data.entity.Application;
import dev.hidly.nubi.application.data.enums.ApplicationStatus;
import dev.hidly.nubi.organization.data.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // 신청에는 담당자가 따로 저장되지 않아 기관의 첫 담당자를 연락처로 쓴다.
    @Query("select m from Member m where m.organization.id in :organizationIds order by m.id")
    List<Member> findMembersOfOrganizations(@Param("organizationIds") Collection<Long> organizationIds);
}
