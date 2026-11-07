package dev.hidly.nubi.organization.data.repository;

import dev.hidly.nubi.organization.data.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
}
