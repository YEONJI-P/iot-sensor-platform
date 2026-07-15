package dev.yeon.iotsensorplatform.global.service;

import dev.yeon.iotsensorplatform.device.entity.Device;
import dev.yeon.iotsensorplatform.device.repository.DeviceRepository;
import dev.yeon.iotsensorplatform.organization.entity.OrgGroup;
import dev.yeon.iotsensorplatform.organization.repository.GroupUserRepository;
import dev.yeon.iotsensorplatform.user.entity.Role;
import dev.yeon.iotsensorplatform.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AccessControlService {

    private final DeviceRepository deviceRepository;
    private final GroupUserRepository groupUserRepository;

    @Transactional(readOnly = true)
    public List<Device> getAccessibleDevices(User user) {
        if (user.getRole() == Role.SYSTEM_ADMIN) {
            return deviceRepository.findAll();
        }
        if (user.getRole() == Role.ORG_ADMIN) {
            if (user.getOrganization() == null) return List.of();
            return deviceRepository.findAllByGroup_Organization_Id(user.getOrganization().getId());
        }
        List<Long> groupIds = getGroupIds(user);
        if (groupIds.isEmpty()) return List.of();
        return deviceRepository.findAllByGroupIdIn(groupIds);
    }

    @Transactional(readOnly = true)
    public List<Long> getAccessibleDeviceIds(User user) {
        if (user.getRole() == Role.SYSTEM_ADMIN) {
            return deviceRepository.findAllIds();
        }
        if (user.getRole() == Role.ORG_ADMIN) {
            if (user.getOrganization() == null) return List.of();
            return deviceRepository.findAllByGroup_Organization_Id(user.getOrganization().getId())
                    .stream().map(Device::getId).toList();
        }
        List<Long> groupIds = getGroupIds(user);
        if (groupIds.isEmpty()) return List.of();
        return deviceRepository.findAllByGroupIdIn(groupIds)
                .stream().map(Device::getId).toList();
    }

    @Transactional(readOnly = true)
    public void assertCanAccessDevice(User user, Device device) {
        if (user.getRole() == Role.SYSTEM_ADMIN) return;
        if (user.getRole() == Role.ORG_ADMIN) {
            if (user.getOrganization() == null ||
                    device.getGroup() == null ||
                    !device.getGroup().getOrganization().getId().equals(user.getOrganization().getId())) {
                throw new AccessDeniedException("접근 권한이 없는 장치예요");
            }
            return;
        }
        List<Long> groupIds = getGroupIds(user);
        if (device.getGroup() == null || !groupIds.contains(device.getGroup().getId())) {
            throw new AccessDeniedException("접근 권한이 없는 장치예요");
        }
    }

    // VIEWER는 읽기 전용 — 장치 등록·수정·삭제 불가
    public void assertCanMutateDevice(User user) {
        if (user.getRole() == Role.VIEWER) {
            throw new AccessDeniedException("열람 전용 계정은 장치를 변경할 수 없어요");
        }
    }

    @Transactional(readOnly = true)
    public void assertCanManageGroup(User user, OrgGroup group) {
        if (user.getRole() == Role.SYSTEM_ADMIN) return;
        if (user.getRole() == Role.ORG_ADMIN) {
            if (user.getOrganization() == null ||
                    !group.getOrganization().getId().equals(user.getOrganization().getId())) {
                throw new AccessDeniedException("본인 조직의 그룹만 관리할 수 있어요");
            }
            return;
        }
        // MEMBER — 자기 그룹만
        boolean inGroup = groupUserRepository.existsByGroupIdAndUserId(group.getId(), user.getId());
        if (!inGroup) {
            throw new AccessDeniedException("소속된 그룹만 관리할 수 있어요");
        }
    }

    private List<Long> getGroupIds(User user) {
        return groupUserRepository.findAllByUserId(user.getId())
                .stream().map(gu -> gu.getGroup().getId()).toList();
    }
}
