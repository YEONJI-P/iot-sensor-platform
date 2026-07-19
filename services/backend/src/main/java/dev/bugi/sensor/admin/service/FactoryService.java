package dev.bugi.sensor.admin.service;

import dev.bugi.sensor.admin.dto.FactoryRequest;
import dev.bugi.sensor.admin.dto.FactoryResponse;
import dev.bugi.sensor.factory.calendar.entity.FactoryOperatingCalendar;
import dev.bugi.sensor.factory.calendar.entity.FactoryWeeklyInterval;
import dev.bugi.sensor.factory.calendar.repository.FactoryOperatingCalendarRepository;
import dev.bugi.sensor.factory.calendar.repository.FactoryWeeklyIntervalRepository;
import dev.bugi.sensor.factory.calendar.repository.FactoryDateOverrideRepository;
import dev.bugi.sensor.factory.calendar.repository.FactoryDateOverrideIntervalRepository;
import dev.bugi.sensor.factory.entity.Factory;
import dev.bugi.sensor.factory.repository.FactoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;

import static dev.bugi.sensor.admin.service.FactoryCalendarAdminService.DEFAULT_RESUME_GRACE_SECONDS;
import static dev.bugi.sensor.admin.service.FactoryCalendarAdminService.DEFAULT_TIMEZONE;

@Service
@RequiredArgsConstructor
public class FactoryService {

    private final FactoryRepository factoryRepository;
    private final FactoryOperatingCalendarRepository calendarRepository;
    private final FactoryWeeklyIntervalRepository weeklyIntervalRepository;
    private final FactoryDateOverrideRepository dateOverrideRepository;
    private final FactoryDateOverrideIntervalRepository dateOverrideIntervalRepository;
    private final Clock clock;

    @Transactional
    public FactoryResponse create(FactoryRequest request) {
        Factory org = Factory.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();
        Factory saved = factoryRepository.save(org);
        Instant now = clock.instant();
        FactoryOperatingCalendar calendar = calendarRepository.save(
                new FactoryOperatingCalendar(saved, DEFAULT_TIMEZONE, DEFAULT_RESUME_GRACE_SECONDS, now));
        weeklyIntervalRepository.saveAll(java.util.Arrays.stream(DayOfWeek.values())
                .map(day -> new FactoryWeeklyInterval(calendar, day.getValue(), 0, 1440))
                .toList());
        return new FactoryResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<FactoryResponse> getAll() {
        return factoryRepository.findAll().stream()
                .map(FactoryResponse::new).toList();
    }

    @Transactional
    public FactoryResponse update(Long id, FactoryRequest request) {
        Factory org = get(id);
        org.update(request.getName(), request.getDescription());
        return new FactoryResponse(org);
    }

    @Transactional
    public void delete(Long id) {
        Factory factory = get(id);
        // prod V6는 ON DELETE CASCADE지만 local ddl-auto 스키마도 같은 동작을 보장하도록 명시적으로 정리한다.
        dateOverrideIntervalRepository.deleteAllByFactoryId(id);
        dateOverrideRepository.deleteAllByFactoryId(id);
        weeklyIntervalRepository.deleteAllByFactoryId(id);
        calendarRepository.findById(id).ifPresent(calendarRepository::delete);
        factoryRepository.delete(factory);
    }

    private Factory get(Long id) {
        return factoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공장이에요"));
    }
}
