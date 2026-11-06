package dev.hidly.nubi.organization.data.repository;

import dev.hidly.nubi.organization.data.entity.Member;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    @EntityGraph(attributePaths = "organization")
    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);
}
