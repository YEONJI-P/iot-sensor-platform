// UserRepository.java
package dev.yeon.iotsensorplatform.user.repository;
import dev.yeon.iotsensorplatform.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}