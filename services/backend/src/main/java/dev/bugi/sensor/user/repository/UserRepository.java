package dev.bugi.sensor.user.repository;

import dev.bugi.sensor.user.entity.Role;
import dev.bugi.sensor.user.entity.User;
import dev.bugi.sensor.user.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByEmployeeId(String employeeId);
    boolean existsByEmployeeId(String employeeId);
    List<User> findAllByStatus(UserStatus status);
    List<User> findAllByFactory_Id(Long factoryId);
    List<User> findAllByFactory_IdAndStatus(Long factoryId, UserStatus status);
    boolean existsByRole(Role role);
}
