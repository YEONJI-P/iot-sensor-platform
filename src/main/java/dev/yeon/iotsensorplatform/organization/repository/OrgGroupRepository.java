package dev.yeon.iotsensorplatform.organization.repository;

import dev.yeon.iotsensorplatform.organization.entity.OrgGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrgGroupRepository extends JpaRepository<OrgGroup, Long> {
    List<OrgGroup> findAllByOrganizationId(Long organizationId);
}
