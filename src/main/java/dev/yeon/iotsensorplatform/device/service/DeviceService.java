package dev.yeon.iotsensorplatform.device.service;

import dev.yeon.iotsensorplatform.device.dto.DeviceRegisterRequest;
import dev.yeon.iotsensorplatform.device.dto.DeviceResponse;
import dev.yeon.iotsensorplatform.device.dto.DeviceUpdateRequest;
import dev.yeon.iotsensorplatform.device.entity.Device;
import dev.yeon.iotsensorplatform.device.repository.DeviceRepository;
import dev.yeon.iotsensorplatform.user.entity.User;
import dev.yeon.iotsensorplatform.user.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceService {
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    // device 등록
    @Transactional
    public DeviceResponse register(DeviceRegisterRequest request,String email){
        // User 객체 확인 > 컨트롤러에서 조회한 사용자 이메일(Unique)로
        // Device 객체 build 하여 repo 에 저장
        // 이 때 user 필드는 위에서 확인한 user 객체로 build 저장
        // 성공시 저장된 device정보로 deviceResponse 생성하여 return
        User user = userRepository.findByEmail(email).orElseThrow(
                // 여기서 user email이 존재하지 않는다는건 token 이슈가 발생한것
                ()-> new IllegalArgumentException("존재하지 않는 이메일이에요."));
        Device device = Device.builder()
                .user(user)
                .name(request.getName())
                .type(request.getType())
                .location(request.getLocation())
                .thresholdValue(request.getThresholdValue())
                .build();
        deviceRepository.save(device);
        return DeviceResponse.from(device);
    }
    // device 정보 수정
    @Transactional
    public DeviceResponse update(Long deviceId, DeviceUpdateRequest request,String email){
        // device Repo에서 findById
        // Device Id에 해당되는 device 객체가 User Email 이 맞는지
        // 찾은 객체 request 정보로 update
        // updateRequest 엔 바뀌지않은 기본정보와 바꾼 정보가 함께 들어와야함
        Device device = deviceRepository.findById(deviceId).orElseThrow(()-> new IllegalArgumentException("장치정보가 존재하지 않아요"));
        if(!device.getUser().getEmail().equals(email)){
            throw new IllegalArgumentException("본인 장치만 수정할 수 있어요");
        }
        device.update(request.getName(),request.getType(),request.getLocation(), request.getThresholdValue());
        deviceRepository.save(device);

        return DeviceResponse.from(device);

    }
    // device 조회응답
    @Transactional(readOnly=true)
    public List<DeviceResponse> getMyDevices(String email){
        // email 정보로 해당 User의 device 목록 전체 조회
        return deviceRepository.findAllByUserEmail(email).stream().map(DeviceResponse::from).toList();

    }
    @Transactional
    public void delete(Long deviceId,String email){
        Device device = deviceRepository.findById(deviceId).orElseThrow(()->new IllegalArgumentException("장치정보가 존재하지 않아요."));
        if(!device.getUser().getEmail().equals(email)){
            throw new IllegalArgumentException("본인 장치만 제거할 수 있어요");
        }
        deviceRepository.delete(device);
    }

}
