package dev.bugi.sensor.admin.service;

import dev.bugi.sensor.admin.dto.ZoneRequest;
import dev.bugi.sensor.admin.dto.ZoneResponse;
import dev.bugi.sensor.global.service.AccessControlService;
import dev.bugi.sensor.factory.entity.ZoneUser;
import dev.bugi.sensor.factory.entity.Zone;
import dev.bugi.sensor.factory.entity.Factory;
import dev.bugi.sensor.factory.repository.ZoneUserRepository;
import dev.bugi.sensor.factory.repository.ZoneRepository;
import dev.bugi.sensor.factory.repository.FactoryRepository;
import dev.bugi.sensor.user.entity.User;
import dev.bugi.sensor.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ZoneService {

    private final ZoneRepository zoneRepository;
    private final FactoryRepository factoryRepository;
    private final ZoneUserRepository zoneUserRepository;
    private final UserRepository userRepository;
    private final AccessControlService accessControlService;

    @Transactional
    public ZoneResponse create(ZoneRequest request, String employeeId) {
        User user = getUser(employeeId);
        Factory org = factoryRepository.findById(request.getFactoryId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공장이에요"));

        Zone zone = Zone.builder()
                .factory(org)
                .name(request.getName())
                .description(request.getDescription())
                .build();
        accessControlService.assertCanManageZone(user, zone);
        return new ZoneResponse(zoneRepository.save(zone));
    }

    @Transactional(readOnly = true)
    public List<ZoneResponse> getAll(String employeeId) {
        return getAccessible(employeeId);
    }

    @Transactional(readOnly = true)
    public List<ZoneResponse> getAccessible(String employeeId) {
        User user = getUser(employeeId);
        return accessControlService.getAccessibleZones(user)
                .stream().map(ZoneResponse::new).toList();
    }

    @Transactional
    public ZoneResponse update(Long zoneId, ZoneRequest request, String employeeId) {
        User user = getUser(employeeId);
        Zone zone = getZone(zoneId);
        accessControlService.assertCanManageZone(user, zone);
        zone.update(request.getName(), request.getDescription());
        return new ZoneResponse(zone);
    }

    @Transactional
    public void delete(Long zoneId, String employeeId) {
        User user = getUser(employeeId);
        Zone zone = getZone(zoneId);
        accessControlService.assertCanManageZone(user, zone);
        zoneRepository.delete(zone);
    }

    @Transactional
    public void addUser(Long zoneId, Long userId, String employeeId) {
        User caller = getUser(employeeId);
        Zone zone = getZone(zoneId);
        accessControlService.assertCanManageZone(caller, zone);

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자예요"));
        if (zoneUserRepository.existsByZoneIdAndUserId(zoneId, userId)) {
            throw new IllegalArgumentException("이미 구역에 속한 사용자예요");
        }
        zoneUserRepository.save(ZoneUser.builder().zone(zone).user(target).build());
    }

    @Transactional
    public void removeUser(Long zoneId, Long userId, String employeeId) {
        User caller = getUser(employeeId);
        Zone zone = getZone(zoneId);
        accessControlService.assertCanManageZone(caller, zone);

        if (!zoneUserRepository.existsByZoneIdAndUserId(zoneId, userId)) {
            throw new IllegalArgumentException("구역에 속하지 않은 사용자예요");
        }
        zoneUserRepository.deleteByZoneIdAndUserId(zoneId, userId);
    }

    private Zone getZone(Long id) {
        return zoneRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 구역이에요"));
    }

    private User getUser(String employeeId) {
        return userRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사원번호예요"));
    }
}
