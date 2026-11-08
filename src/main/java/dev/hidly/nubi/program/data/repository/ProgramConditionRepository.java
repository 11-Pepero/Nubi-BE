package dev.hidly.nubi.program.data.repository;

import dev.hidly.nubi.program.data.entity.ProgramCondition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProgramConditionRepository extends JpaRepository<ProgramCondition, Long> {
    List<ProgramCondition> findAllByProgramIdOrderByIdAsc(Long programId);
}
