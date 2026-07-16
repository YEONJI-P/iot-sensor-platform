package dev.bugi.sensor.factory.repository;

import dev.bugi.sensor.factory.entity.Factory;
import dev.bugi.sensor.factory.entity.Zone;
import dev.bugi.sensor.factory.entity.ZoneUser;
import dev.bugi.sensor.support.AbstractPostgresTest;
import dev.bugi.sensor.user.entity.Role;
import dev.bugi.sensor.user.entity.User;
import dev.bugi.sensor.user.entity.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ZoneUser 의 (zone_id, user_id) unique 제약이 앱 레벨(existsBy...)이 아니라
 * DB 레벨에서 실제로 걸리는지 검증한다.
 */
class ZoneUserRepositoryTest extends AbstractPostgresTest {

    @Autowired
    ZoneUserRepository zoneUserRepository;

    @Autowired
    TestEntityManager tem;

    @Test
    void 같은_zone과_user조합을_두번_저장하면_DB_unique제약이_막는다() {
        Factory factory = tem.persist(Factory.builder().name("F").description(null).build());
        Zone zone = tem.persist(Zone.builder().factory(factory).name("Z").description(null).build());
        User user = tem.persist(User.builder()
                .employeeId("emp-1").name("u").email("u@e.com").password("p")
                .factory(factory).role(Role.MEMBER).status(UserStatus.ACTIVE)
                .build());

        zoneUserRepository.saveAndFlush(ZoneUser.builder().zone(zone).user(user).build());

        ZoneUser dup = ZoneUser.builder().zone(zone).user(user).build();
        assertThatThrownBy(() -> zoneUserRepository.saveAndFlush(dup))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 다른_user면_같은_zone에_중복없이_저장된다() {
        Factory factory = tem.persist(Factory.builder().name("F").description(null).build());
        Zone zone = tem.persist(Zone.builder().factory(factory).name("Z").description(null).build());
        User u1 = tem.persist(User.builder()
                .employeeId("emp-1").name("u1").email("u1@e.com").password("p")
                .factory(factory).role(Role.MEMBER).status(UserStatus.ACTIVE).build());
        User u2 = tem.persist(User.builder()
                .employeeId("emp-2").name("u2").email("u2@e.com").password("p")
                .factory(factory).role(Role.MEMBER).status(UserStatus.ACTIVE).build());

        zoneUserRepository.saveAndFlush(ZoneUser.builder().zone(zone).user(u1).build());
        zoneUserRepository.saveAndFlush(ZoneUser.builder().zone(zone).user(u2).build());

        assertThat(zoneUserRepository.findAllByZoneId(zone.getId())).hasSize(2);
    }
}
