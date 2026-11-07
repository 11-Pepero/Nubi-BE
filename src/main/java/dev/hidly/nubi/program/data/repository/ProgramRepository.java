package dev.hidly.nubi.program.data.repository;

import dev.hidly.nubi.program.data.entity.Program;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgramRepository extends JpaRepository<Program, Long> {
}
