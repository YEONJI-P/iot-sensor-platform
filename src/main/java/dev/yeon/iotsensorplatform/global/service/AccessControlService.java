package dev.yeon.iotsensorplatform.global.service;

import dev.yeon.iotsensorplatform.device.entity.Device;
import dev.yeon.iotsensorplatform.device.repository.DeviceRepository;
import dev.yeon.iotsensorplatform.organization.entity.OrgGroup;
import dev.yeon.iotsensorplatform.organization.repository.GroupUserRepository;
import dev.yeon.iotsensorplatform.user.entity.Role;
import dev.yeon.iotsensorplatform.user.entity.User;
import lombok.RequiredArgsConstructor;
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
        if (user.getRole() == Role.SUPER_ADMIN) {
            return deviceRepository.findAll();
        }
        if (user.getRole() == Role.USER_ADMIN) {
            if (user.getOrganization() == null) return List.of();
            return deviceRepository.findAllByGroup_Organization_Id(user.getOrganization().getId());
        }
        List<Long> groupIds = getGroupIds(user);
        if (groupIds.isEmpty()) return List.of();
        return deviceRepository.findAllByGroupIdIn(groupIds);
    }

    @Transactional(readOnly = true)
    public List<Long> getAccessibleDeviceIds(User user) {
        if (user.getRole() == Role.SUPER_ADMIN) {
            return deviceRepository.findAllIds();
        }
        if (user.getRole() == Role.USER_ADMIN) {
            if (user.getOrganization() == null) return List.of();
            return deviceRepository.findAllByGroup_Organization_Id(user.getOrganization().getId())
                    .stream().map(Device::getId).toList();
        }
        List<Long> groupIds = getGroupIds(user);
        if (groupIds.isEmpty()) return List.of();
        return deviceRepository.findAllByGroupIdIn(groupIds)
                .stream().map(Device::getId).toList();
    }

    public void assertCanAccessDevice(User user, Device device) {
        if (user.getRole() == Role.SUPER_ADMIN) return;
        if (user.getRole() == Role.USER_ADMIN) {
            if (user.getOrganization() == null ||
                    device.getGroup() == null ||
                    !device.getGroup().getOrganization().getId().equals(user.getOrganization().getId())) {
                throw new IllegalArgumentException("접근 권한이 없는 장치예요");
            }
            return;
        }
        List<Long> groupIds = getGroupIds(user);
        if (device.getGroup() == null || !groupIds.contains(device.getGroup().getId())) {
            throw new IllegalArgumentException("접근 권한이 없는 장치예요");
        }
    }

    public void assertCanManageGroup(User user, OrgGroup group) {
        if (user.getRole() == Role.SUPER_ADMIN) return;
        if (user.getRole() == Role.USER_ADMIN) {
            if (user.getOrganization() == null ||
                    !group.getOrganization().getId().equals(user.getOrganization().getId())) {
                throw new IllegalArgumentException("본인 조직의 그룹만 관리할 수 있어요");
            }
            return;
        }
        // DEVICE_MANAGER, DATA_INPUTTER 등 — 자기 그룹만
        boolean inGroup = groupUserRepository.existsByGroupIdAndUserId(group.getId(), user.getId());
        if (!inGroup) {
            throw new IllegalArgumentException("소속된 그룹만 관리할 수 있어요");
        }
    }

    private List<Long> getGroupIds(User user) {
        return groupUserRepository.findAllByUserId(user.getId())
                .stream().map(gu -> gu.getGroup().getId()).toList();
    }
}
