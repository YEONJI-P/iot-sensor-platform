package dev.yeon.iotsensorplatform.organization.repository;

import dev.yeon.iotsensorplatform.organization.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    boolean existsByName(String name);
}
