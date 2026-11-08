package dev.hidly.nubi.application.data.repository;

import dev.hidly.nubi.application.data.entity.Application;
import dev.hidly.nubi.application.data.enums.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    boolean existsByProgramIdAndGroupIdAndStatusIn(Long programId, Long groupId, Collection<ApplicationStatus> statuses);

    @Query("""
            select coalesce(sum(a.participantCount), 0) from Application a
            where a.program.id = :programId and a.status not in :released
            """)
    long sumParticipantsExcept(@Param("programId") Long programId,
                               @Param("released") Collection<ApplicationStatus> released);
}
