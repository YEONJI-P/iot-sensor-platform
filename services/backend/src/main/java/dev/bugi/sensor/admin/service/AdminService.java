package dev.bugi.sensor.admin.service;

import dev.bugi.sensor.admin.dto.ApproveRequest;
import dev.bugi.sensor.admin.dto.UserResponse;
import dev.bugi.sensor.factory.entity.Factory;
import dev.bugi.sensor.factory.entity.Zone;
import dev.bugi.sensor.factory.entity.ZoneUser;
import dev.bugi.sensor.factory.repository.ZoneRepository;
import dev.bugi.sensor.factory.repository.ZoneUserRepository;
import dev.bugi.sensor.global.service.AccessControlService;
import dev.bugi.sensor.user.entity.Role;
import dev.bugi.sensor.user.entity.User;
import dev.bugi.sensor.user.entity.UserStatus;
import dev.bugi.sensor.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final ZoneRepository zoneRepository;
    private final ZoneUserRepository zoneUserRepository;
    private final AccessControlService accessControlService;

    @Transactional(readOnly = true)
    public List<UserResponse> getPendingUsers(String employeeId) {
        User caller = getCaller(employeeId);
        List<User> users = isSystemAdmin(caller)
                ? userRepository.findAllByStatus(UserStatus.PENDING)
                : userRepository.findAllByFactory_IdAndStatus(callerFactoryId(caller), UserStatus.PENDING);
        return users.stream().map(UserResponse::new).toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers(String employeeId) {
        User caller = getCaller(employeeId);
        List<User> users = isSystemAdmin(caller)
                ? userRepository.findAll()
                : userRepository.findAllByFactory_Id(callerFactoryId(caller));
        return users.stream().map(UserResponse::new).toList();
    }

    // 승인 = 상태 ACTIVE + 역할 부여 + 구역 배정을 한 트랜잭션에서.
    @Transactional
    public void approveUser(Long id, ApproveRequest request, String employeeId) {
        User caller = getCaller(employeeId);
        User target = getUser(id);
        assertCanManage(caller, target);
        if (target.getStatus() != UserStatus.PENDING) {
            throw new IllegalArgumentException("대기 중인 사용자만 승인할 수 있어요");
        }
        assertCanGrantRole(caller, request.role());

        // 배정할 구역 검증(관리 권한 + 동일 공장). 구역이 있으면 사용자 공장도 그 공장으로 맞춘다.
        List<Zone> zones = request.zoneIds().stream().map(this::getZone).toList();
        Factory factory = validateZonesSameFactory(caller, zones);

        target.approve(request.role());
        if (factory != null) {
            target.assignFactory(factory);
        }
        for (Zone zone : zones) {
            if (!zoneUserRepository.existsByZoneIdAndUserId(zone.getId(), target.getId())) {
                zoneUserRepository.save(ZoneUser.builder().zone(zone).user(target).build());
            }
        }
    }

    @Transactional
    public void rejectUser(Long id, String employeeId) {
        User target = getUser(id);
        assertCanManage(getCaller(employeeId), target);
        if (target.getStatus() != UserStatus.PENDING) {
            throw new IllegalArgumentException("대기 중인 사용자만 반려할 수 있어요");
        }
        target.reject();
    }

    // 권한 상승 차단: FACTORY_ADMIN은 VIEWER/MEMBER까지만, SYSTEM_ADMIN은 FACTORY_ADMIN까지.
    // (SYSTEM_ADMIN 부여는 승인 경로로 불가 — seed 전용)
    private void assertCanGrantRole(User caller, Role role) {
        boolean allowed = isSystemAdmin(caller)
                ? (role == Role.VIEWER || role == Role.MEMBER || role == Role.FACTORY_ADMIN)
                : (role == Role.VIEWER || role == Role.MEMBER);
        if (!allowed) {
            throw new AccessDeniedException("부여할 수 없는 역할이에요");
        }
    }

    // 각 구역에 대한 관리 권한을 확인(SYSTEM_ADMIN=전체, FACTORY_ADMIN=자기 공장)하고,
    // 모든 구역이 같은 공장인지 검증한 뒤 그 공장을 반환. 구역이 없으면 null.
    private Factory validateZonesSameFactory(User caller, List<Zone> zones) {
        Factory factory = null;
        for (Zone zone : zones) {
            accessControlService.assertCanManageZone(caller, zone);
            if (factory == null) {
                factory = zone.getFactory();
            } else if (!factory.getId().equals(zone.getFactory().getId())) {
                throw new IllegalArgumentException("서로 다른 공장의 구역은 함께 배정할 수 없어요");
            }
        }
        return factory;
    }

    // FACTORY_ADMIN은 자기 공장 소속 사용자만 관리 가능. SYSTEM_ADMIN은 전체.
    private void assertCanManage(User caller, User target) {
        if (isSystemAdmin(caller)) {
            return;
        }
        Long callerFactory = callerFactoryId(caller);
        Long targetFactory = target.getFactory() != null ? target.getFactory().getId() : null;
        if (!callerFactory.equals(targetFactory)) {
            throw new AccessDeniedException("소속 공장의 사용자만 관리할 수 있어요");
        }
    }

    private boolean isSystemAdmin(User user) {
        return user.getRole() == Role.SYSTEM_ADMIN;
    }

    // FACTORY_ADMIN이 공장에 배정돼 있어야 스코핑이 성립한다.
    private Long callerFactoryId(User caller) {
        if (caller.getFactory() == null) {
            throw new AccessDeniedException("소속 공장이 없어 사용자 관리 권한이 없어요");
        }
        return caller.getFactory().getId();
    }

    private Zone getZone(Long id) {
        return zoneRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 구역이에요 - id " + id));
    }

    private User getCaller(String employeeId) {
        return userRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자예요"));
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자예요"));
    }
}
