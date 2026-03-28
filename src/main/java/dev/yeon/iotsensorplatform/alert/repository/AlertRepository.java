package dev.yeon.iotsensorplatform.alert.repository;

import dev.yeon.iotsensorplatform.alert.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findAllByDeviceIdOrderByCreatedAtDesc(Long deviceId);
    List<Alert> findAllByDeviceIdInOrderByCreatedAtDesc(List<Long> deviceIds);

    @Query(value = """
            SELECT *
            FROM alert
            WHERE device_id = :deviceId
            ORDER BY created_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Alert> findRecentByDeviceId(@Param("deviceId") Long deviceId, @Param("limit") int limit);

    @Query(value = """
            SELECT to_char(created_at, 'YYYY-MM-DD') AS date,
                   COUNT(*)                          AS count
            FROM alert
            WHERE device_id = :deviceId
              AND created_at >= :startDate
            GROUP BY to_char(created_at, 'YYYY-MM-DD')
            ORDER BY date ASC
            """, nativeQuery = true)
    List<Object[]> findDailyCountByDeviceId(@Param("deviceId") Long deviceId,
                                             @Param("startDate") LocalDateTime startDate);
}
