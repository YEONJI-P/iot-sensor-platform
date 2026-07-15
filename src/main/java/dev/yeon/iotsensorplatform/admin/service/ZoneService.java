package dev.yeon.iotsensorplatform.admin.service;

import dev.yeon.iotsensorplatform.admin.dto.ZoneRequest;
import dev.yeon.iotsensorplatform.admin.dto.ZoneResponse;
import dev.yeon.iotsensorplatform.global.service.AccessControlService;
import dev.yeon.iotsensorplatform.factory.entity.ZoneUser;
import dev.yeon.iotsensorplatform.factory.entity.Zone;
import dev.yeon.iotsensorplatform.factory.entity.Factory;
import dev.yeon.iotsensorplatform.factory.repository.ZoneUserRepository;
import dev.yeon.iotsensorplatform.factory.repository.ZoneRepository;
import dev.yeon.iotsensorplatform.factory.repository.FactoryRepository;
import dev.yeon.iotsensorplatform.user.entity.User;
import dev.yeon.iotsensorplatform.user.repository.UserRepository;
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
        User user = getUser(employeeId);
        if (user.getFactory() == null) {
            return zoneRepository.findAll().stream().map(ZoneResponse::new).toList();
        }
        return zoneRepository.findAllByFactoryId(user.getFactory().getId())
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
