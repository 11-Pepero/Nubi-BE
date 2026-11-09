package dev.hidly.nubi.program.data.repository;

import dev.hidly.nubi.program.data.entity.ProgramCondition;
import dev.hidly.nubi.program.data.enums.ConditionSource;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProgramConditionRepository extends JpaRepository<ProgramCondition, Long> {
    List<ProgramCondition> findAllByProgramIdOrderByIdAsc(Long programId);

    // 행사 수정 때 주최가 넣은 조건(HOST)만 지우고 다시 넣는다. AI·후기 출처 조건은 남긴다.
    @Modifying(flushAutomatically = true)
    @Query("delete from ProgramCondition c where c.program.id = :programId and c.source = :source")
    int deleteByProgramIdAndSource(@Param("programId") Long programId, @Param("source") ConditionSource source);

    @EntityGraph(attributePaths = "supportTag")
    List<ProgramCondition> findAllByProgramIdIn(Collection<Long> programIds);
}
