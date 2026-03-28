package dev.yeon.iotsensorplatform.organization.repository;

import dev.yeon.iotsensorplatform.organization.entity.GroupUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupUserRepository extends JpaRepository<GroupUser, Long> {
    List<GroupUser> findAllByUserId(Long userId);
    List<GroupUser> findAllByGroupId(Long groupId);
    boolean existsByGroupIdAndUserId(Long groupId, Long userId);
    void deleteByGroupIdAndUserId(Long groupId, Long userId);
}
