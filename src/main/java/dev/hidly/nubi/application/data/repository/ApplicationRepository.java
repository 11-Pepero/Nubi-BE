package dev.hidly.nubi.application.data.repository;

import dev.hidly.nubi.application.data.entity.Application;
import dev.hidly.nubi.application.data.enums.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    boolean existsByProgramIdAndGroupIdAndStatusIn(Long programId, Long groupId, Collection<ApplicationStatus> statuses);

    @Query("""
            select coalesce(sum(a.participantCount), 0) from Application a
            where a.program.id = :programId and a.status not in :released
            """)
    long sumParticipantsExcept(@Param("programId") Long programId,
                               @Param("released") Collection<ApplicationStatus> released);

    // 마감 알림용: 모집 중인 프로그램 중 마감이 [from, to)에 있고 아직 작성 중인 신청
    @Query("""
            select a from Application a join fetch a.program p
            where a.status = dev.hidly.nubi.application.data.enums.ApplicationStatus.PREPARING
              and p.status = dev.hidly.nubi.program.data.enums.ProgramStatus.OPEN
              and p.deletedAt is null
              and p.deadlineAt >= :from and p.deadlineAt < :to
            """)
    List<Application> findPreparingByDeadlineBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
