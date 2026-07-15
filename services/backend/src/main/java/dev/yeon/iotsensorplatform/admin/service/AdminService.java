package dev.yeon.iotsensorplatform.admin.service;

import dev.yeon.iotsensorplatform.admin.dto.UserResponse;
import dev.yeon.iotsensorplatform.user.entity.Role;
import dev.yeon.iotsensorplatform.user.entity.User;
import dev.yeon.iotsensorplatform.user.entity.UserStatus;
import dev.yeon.iotsensorplatform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;

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

    @Transactional
    public void approveUser(Long id, String employeeId) {
        User target = getUser(id);
        assertCanManage(getCaller(employeeId), target);
        if (target.getStatus() != UserStatus.PENDING) {
            throw new IllegalArgumentException("대기 중인 사용자만 승인할 수 있어요");
        }
        target.approve();
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

    private User getCaller(String employeeId) {
        return userRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자예요"));
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자예요"));
    }
}
