package dev.hidly.nubi.program.data.repository;

import dev.hidly.nubi.program.data.entity.Eligibility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EligibilityRepository extends JpaRepository<Eligibility, Long> {
    List<Eligibility> findAllByGroupId(Long groupId);
}
