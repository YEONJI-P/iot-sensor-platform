package dev.yeon.iotsensorplatform.device.service;

import dev.yeon.iotsensorplatform.device.dto.DeviceRegisterRequest;
import dev.yeon.iotsensorplatform.device.dto.DeviceUpdateRequest;
import dev.yeon.iotsensorplatform.device.entity.Device;
import dev.yeon.iotsensorplatform.device.repository.DeviceRepository;
import dev.yeon.iotsensorplatform.user.entity.User;
import dev.yeon.iotsensorplatform.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// User 정보 유효성은 JWTUtil에서 하므로 이 테스트에서는 User정보 별도 유효검증 X
@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    DeviceRepository deviceRepository;
    @Mock
    UserRepository userRepository;

    @InjectMocks
    DeviceService deviceService;
    // 등록 성공
    @Test
    void register() {
        // === given
        // User, DeviceRegisterRequest
        User mockUser = User.builder().email("test@test.com").password("password123").role(User.Role.USER).build();
        DeviceRegisterRequest request = new DeviceRegisterRequest(
                "온도센서1",
                Device.DeviceType.TEMPERATURE,
                "공장1층",
                80.0
        );
        // user 검증하면 통과
        when(userRepository.findByEmail(mockUser.getEmail())).thenReturn(Optional.of(mockUser));
        // === when
        // service register 함수를 실행
        deviceService.register(request,mockUser.getEmail());
        // ===then
        // DB save됐는지 검사
        verify(deviceRepository,times(1)).save(any(Device.class));

    }

    @Test
    void update_success() {
        // === given
        // authenticationPrincipal 로 전달된 user 정보
        User mockUser = User.builder().email("test@test.com").password("password123").role(User.Role.USER).build();

        Device mockDevice = Device.builder()
                .user(mockUser)
                .name("온도센서1")
                .type(Device.DeviceType.TEMPERATURE)
                .location("공장1층")
                .thresholdValue(80.0)
                .build();
        // pathVariable deviceId
        Long deviceId = 1L;

        DeviceUpdateRequest request = new DeviceUpdateRequest(
                "온도센서1_update",
                Device.DeviceType.TEMPERATURE,
                "공장2층",
                75.0
        );

        // device 가 존재한다고 가정
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(mockDevice));
        // mock device에 mockUser 정보를 넣었으므로 소유권확인 로직은 통과

        // === when
        deviceService.update(deviceId,request,mockUser.getEmail());
        // === then
        verify(deviceRepository,times(1)).save(any(Device.class));

    }
    @Test
    void update_fail_device_id() {
        // device가 존재하지 않을 때
        // === given
        // authenticationPrincipal 로 전달된 user 정보
        User mockUser = User.builder().email("test@test.com").password("password123").role(User.Role.USER).build();
        // pathVariable deviceId
        Long deviceId = 1L;

        DeviceUpdateRequest request = new DeviceUpdateRequest(
                "온도센서1_update",
                Device.DeviceType.TEMPERATURE,
                "공장2층",
                75.0
        );
        // device 가 존재하지 않는다고 가정
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.empty());
        // === when
        // === then
        assertThrows(IllegalArgumentException.class,()->deviceService.update(deviceId,request,mockUser.getEmail()));
    }

    @Test
    void update_fail_email() {
        // 소유자가 내가 아닐때
        // === given
        // authenticationPrincipal 로 전달된 user 정보
        User mockUser = User.builder().email("test@test.com").password("password123").role(User.Role.USER).build();

        Device mockDevice = Device.builder()
                .user(mockUser)
                .name("온도센서1")
                .type(Device.DeviceType.TEMPERATURE)
                .location("공장1층")
                .thresholdValue(80.0)
                .build();
        // pathVariable deviceId
        Long deviceId = 1L;

        DeviceUpdateRequest request = new DeviceUpdateRequest(
                "온도센서1_update",
                Device.DeviceType.TEMPERATURE,
                "공장2층",
                75.0
        );
        // device 가 존재한다고 가정
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(mockDevice));
        // === when
        // === then
        // 다른 이메일일 때 throw 하는지 체크
        assertThrows(IllegalArgumentException.class,()->deviceService.update(deviceId,request,"other@test.com"));
    }

    @Test
    void delete_success() {

        User mockUser = User.builder().email("test@test.com").password("password123").role(User.Role.USER).build();
        Device mockDevice = Device.builder()
                .user(mockUser)
                .name("온도센서1")
                .type(Device.DeviceType.TEMPERATURE)
                .location("공장1층")
                .thresholdValue(80.0)
                .build();
        // pathVariable deviceId
        Long deviceId = 1L;

        // device 가 존재한다고 가정
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(mockDevice));
        // mock device에 mockUser 정보를 넣었으므로 소유권확인 로직은 통과

        // === when
        deviceService.delete(deviceId,mockUser.getEmail());
        // === then
        verify(deviceRepository,times(1)).delete(mockDevice);
    }

    @Test
    void delete_fail_device_id() {
        // device가 존재하지 않을 때
        // === given
        // authenticationPrincipal 로 전달된 user 정보
        User mockUser = User.builder().email("test@test.com").password("password123").role(User.Role.USER).build();
        // pathVariable deviceId
        Long deviceId = 1L;

        // device 가 존재하지 않는다고 가정
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.empty());
        // === when
        // === then
        assertThrows(IllegalArgumentException.class,()->deviceService.delete(deviceId,mockUser.getEmail()));
    }

    @Test
    void delete_fail_email() {
        // 소유자가 내가 아닐때
        // === given
        // authenticationPrincipal 로 전달된 user 정보
        User mockUser = User.builder().email("test@test.com").password("password123").role(User.Role.USER).build();

        Device mockDevice = Device.builder()
                .user(mockUser)
                .name("온도센서1")
                .type(Device.DeviceType.TEMPERATURE)
                .location("공장1층")
                .thresholdValue(80.0)
                .build();
        // pathVariable deviceId
        Long deviceId = 1L;

        // device 가 존재한다고 가정
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(mockDevice));
        // === when
        // === then
        // 다른 이메일일 때 throw 하는지 체크
        assertThrows(IllegalArgumentException.class,()->deviceService.delete(deviceId,"other@test.com"));
    }
}