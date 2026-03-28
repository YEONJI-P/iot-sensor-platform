package dev.yeon.iotsensorplatform.simulator.service;

import dev.yeon.iotsensorplatform.device.entity.Device;
import dev.yeon.iotsensorplatform.device.repository.DeviceRepository;
import dev.yeon.iotsensorplatform.global.service.AccessControlService;
import dev.yeon.iotsensorplatform.sensordata.dto.SensorDataRequest;
import dev.yeon.iotsensorplatform.sensordata.service.SensorDataService;
import dev.yeon.iotsensorplatform.simulator.dto.SimulatorStartRequest;
import dev.yeon.iotsensorplatform.simulator.dto.SimulatorStatusResponse;
import dev.yeon.iotsensorplatform.user.entity.User;
import dev.yeon.iotsensorplatform.user.repository.UserRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SimulatorService {

    private static final int MAX_EXECUTION_MINUTES = 10;
    private static final int MAX_COUNT = 100;

    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final SensorDataService sensorDataService;
    private final AccessControlService accessControlService;

    private final ConcurrentHashMap<Long, SimulatorTask> taskMap = new ConcurrentHashMap<>();
    private final ExecutorService taskExecutor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Random random = new Random();

    @Transactional
    public void start(String employeeId, SimulatorStartRequest request) {
        Long deviceId = request.getDeviceId();

        SimulatorTask existing = taskMap.get(deviceId);
        if (existing != null && existing.isRunning()) {
            throw new IllegalStateException("이미 실행 중인 장치예요 (deviceId: " + deviceId + ")");
        }

        int count = Math.max(1, Math.min(request.getCount(), MAX_COUNT));
        int intervalSeconds = Math.max(1, request.getIntervalSeconds());

        User user = getUser(employeeId);
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 장치예요"));
        accessControlService.assertCanAccessDevice(user, device);

        SimulatorTask task = new SimulatorTask(count);
        taskMap.put(deviceId, task);

        Future<?> future = taskExecutor.submit(() -> runTask(task, device, intervalSeconds, count));
        task.setFuture(future);

        scheduler.schedule(() -> {
            if (task.isRunning()) {
                log.warn("시뮬레이터 최대 실행 시간(10분) 초과 — 강제 종료 (deviceId: {})", deviceId);
                task.cancel();
            }
        }, MAX_EXECUTION_MINUTES, TimeUnit.MINUTES);
    }

    private void runTask(SimulatorTask task, Device device, int intervalSeconds, int count) {
        try {
            for (int i = 0; i < count; i++) {
                if (Thread.currentThread().isInterrupted() || !task.isRunning()) break;

                double value = generateValue(device.getThresholdValue());
                sensorDataService.receive(new SensorDataRequest(device.getId(), value));
                task.incrementCompleted();

                log.debug("시뮬레이터 전송 [{}/{}] deviceId={} value={}",
                        task.getCompletedCount().get(), count, device.getId(),
                        String.format("%.2f", value));

                if (i < count - 1) {
                    Thread.sleep(intervalSeconds * 1000L);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("시뮬레이터 인터럽트 (deviceId: {})", device.getId());
        } finally {
            task.markDone();
            scheduler.schedule(() -> taskMap.remove(device.getId()), 30, TimeUnit.SECONDS);
        }
    }

    private double generateValue(Double threshold) {
        if (threshold == null || threshold <= 0) threshold = 100.0;
        if (random.nextDouble() < 0.8) {
            return Math.round(random.nextDouble() * threshold * 0.95 * 10.0) / 10.0;
        } else {
            return Math.round((threshold * 1.01 + random.nextDouble() * threshold * 0.49) * 10.0) / 10.0;
        }
    }

    public void stop(Long deviceId) {
        SimulatorTask task = taskMap.get(deviceId);
        if (task == null || !task.isRunning()) {
            throw new IllegalArgumentException("실행 중인 시뮬레이터가 없어요 (deviceId: " + deviceId + ")");
        }
        task.cancel();
    }

    public SimulatorStatusResponse getStatus(Long deviceId) {
        SimulatorTask task = taskMap.get(deviceId);
        if (task == null) {
            return new SimulatorStatusResponse(false, 0, 0);
        }
        return new SimulatorStatusResponse(
                task.isRunning(),
                task.getCompletedCount().get(),
                task.getTotalCount()
        );
    }

    @Transactional(readOnly = true)
    public List<Device> getDevices(String employeeId) {
        User user = getUser(employeeId);
        return accessControlService.getAccessibleDevices(user);
    }

    private User getUser(String employeeId) {
        return userRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사원번호예요"));
    }

    @PreDestroy
    public void shutdown() {
        taskExecutor.shutdownNow();
        scheduler.shutdownNow();
    }
}
