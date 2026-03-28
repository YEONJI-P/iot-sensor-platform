package dev.yeon.iotsensorplatform.admin.service;

import dev.yeon.iotsensorplatform.admin.dto.GroupRequest;
import dev.yeon.iotsensorplatform.admin.dto.GroupResponse;
import dev.yeon.iotsensorplatform.global.service.AccessControlService;
import dev.yeon.iotsensorplatform.organization.entity.GroupUser;
import dev.yeon.iotsensorplatform.organization.entity.OrgGroup;
import dev.yeon.iotsensorplatform.organization.entity.Organization;
import dev.yeon.iotsensorplatform.organization.repository.GroupUserRepository;
import dev.yeon.iotsensorplatform.organization.repository.OrgGroupRepository;
import dev.yeon.iotsensorplatform.organization.repository.OrganizationRepository;
import dev.yeon.iotsensorplatform.user.entity.User;
import dev.yeon.iotsensorplatform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrgGroupService {

    private final OrgGroupRepository orgGroupRepository;
    private final OrganizationRepository organizationRepository;
    private final GroupUserRepository groupUserRepository;
    private final UserRepository userRepository;
    private final AccessControlService accessControlService;

    @Transactional
    public GroupResponse create(GroupRequest request, String employeeId) {
        User user = getUser(employeeId);
        Organization org = organizationRepository.findById(request.getOrganizationId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 조직이에요"));

        OrgGroup group = OrgGroup.builder()
                .organization(org)
                .name(request.getName())
                .description(request.getDescription())
                .build();
        accessControlService.assertCanManageGroup(user, group);
        return new GroupResponse(orgGroupRepository.save(group));
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> getAll(String employeeId) {
        User user = getUser(employeeId);
        if (user.getOrganization() == null) {
            return orgGroupRepository.findAll().stream().map(GroupResponse::new).toList();
        }
        return orgGroupRepository.findAllByOrganizationId(user.getOrganization().getId())
                .stream().map(GroupResponse::new).toList();
    }

    @Transactional
    public GroupResponse update(Long groupId, GroupRequest request, String employeeId) {
        User user = getUser(employeeId);
        OrgGroup group = getGroup(groupId);
        accessControlService.assertCanManageGroup(user, group);
        group.update(request.getName(), request.getDescription());
        return new GroupResponse(group);
    }

    @Transactional
    public void delete(Long groupId, String employeeId) {
        User user = getUser(employeeId);
        OrgGroup group = getGroup(groupId);
        accessControlService.assertCanManageGroup(user, group);
        orgGroupRepository.delete(group);
    }

    @Transactional
    public void addUser(Long groupId, Long userId, String employeeId) {
        User caller = getUser(employeeId);
        OrgGroup group = getGroup(groupId);
        accessControlService.assertCanManageGroup(caller, group);

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자예요"));
        if (groupUserRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new IllegalArgumentException("이미 그룹에 속한 사용자예요");
        }
        groupUserRepository.save(GroupUser.builder().group(group).user(target).build());
    }

    @Transactional
    public void removeUser(Long groupId, Long userId, String employeeId) {
        User caller = getUser(employeeId);
        OrgGroup group = getGroup(groupId);
        accessControlService.assertCanManageGroup(caller, group);

        if (!groupUserRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new IllegalArgumentException("그룹에 속하지 않은 사용자예요");
        }
        groupUserRepository.deleteByGroupIdAndUserId(groupId, userId);
    }

    private OrgGroup getGroup(Long id) {
        return orgGroupRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 그룹이에요"));
    }

    private User getUser(String employeeId) {
        return userRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사원번호예요"));
    }
}
