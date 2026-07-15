package dev.yeon.iotsensorplatform.admin.service;

import dev.yeon.iotsensorplatform.admin.dto.UserResponse;
import dev.yeon.iotsensorplatform.user.entity.User;
import dev.yeon.iotsensorplatform.user.entity.UserStatus;
import dev.yeon.iotsensorplatform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserResponse> getPendingUsers() {
        return userRepository.findAllByStatus(UserStatus.PENDING).stream()
                .map(UserResponse::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserResponse::new)
                .toList();
    }

    @Transactional
    public void approveUser(Long id) {
        User user = getUser(id);
        if (user.getStatus() != UserStatus.PENDING) {
            throw new IllegalArgumentException("대기 중인 사용자만 승인할 수 있어요");
        }
        user.approve();
    }

    @Transactional
    public void rejectUser(Long id) {
        User user = getUser(id);
        if (user.getStatus() != UserStatus.PENDING) {
            throw new IllegalArgumentException("대기 중인 사용자만 반려할 수 있어요");
        }
        user.reject();
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자예요"));
    }
}
