package dev.bugi.sensor.factory.calendar.service;

import dev.bugi.sensor.factory.calendar.entity.FactoryDateOverride;
import dev.bugi.sensor.factory.calendar.entity.FactoryDateOverrideInterval;
import dev.bugi.sensor.factory.calendar.entity.FactoryOperatingCalendar;
import dev.bugi.sensor.factory.calendar.entity.FactoryWeeklyInterval;
import dev.bugi.sensor.factory.calendar.repository.FactoryDateOverrideIntervalRepository;
import dev.bugi.sensor.factory.calendar.repository.FactoryDateOverrideRepository;
import dev.bugi.sensor.factory.calendar.repository.FactoryOperatingCalendarRepository;
import dev.bugi.sensor.factory.calendar.repository.FactoryWeeklyIntervalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OperatingCalendarService {

    private static final int CONTINUITY_LOOKBACK_DAYS = 3;

    private final FactoryOperatingCalendarRepository calendarRepository;
    private final FactoryWeeklyIntervalRepository weeklyIntervalRepository;
    private final FactoryDateOverrideRepository overrideRepository;
    private final FactoryDateOverrideIntervalRepository overrideIntervalRepository;

    @Transactional(readOnly = true)
    public Map<Long, OperatingDecision> evaluate(Collection<Long> factoryIds, Instant now) {
        if (factoryIds.isEmpty()) return Map.of();

        List<FactoryOperatingCalendar> calendars = calendarRepository.findAllWithFactoryByFactoryIdIn(factoryIds);
        Map<Long, FactoryOperatingCalendar> calendarsByFactory = new HashMap<>();
        calendars.forEach(calendar -> calendarsByFactory.put(calendar.getFactoryId(), calendar));

        Map<Long, List<MinuteInterval>> weeklyByFactoryDay = new HashMap<>();
        for (FactoryWeeklyInterval interval : weeklyIntervalRepository.findAllByFactoryIdIn(factoryIds)) {
            long key = dayKey(interval.getCalendar().getFactoryId(), interval.getIsoDay());
            weeklyByFactoryDay.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(new MinuteInterval(interval.getStartMinute(), interval.getEndMinute()));
        }

        LocalDate utcDate = now.atZone(ZoneOffset.UTC).toLocalDate();
        List<FactoryDateOverride> overrides = overrideRepository.findAllInRange(
                factoryIds, utcDate.minusDays(CONTINUITY_LOOKBACK_DAYS + 1L), utcDate.plusDays(2));
        Map<Long, List<FactoryDateOverrideInterval>> intervalsByOverride = new HashMap<>();
        List<Long> overrideIds = overrides.stream().map(FactoryDateOverride::getId).toList();
        if (!overrideIds.isEmpty()) {
            for (FactoryDateOverrideInterval interval : overrideIntervalRepository.findAllByOverrideIdIn(overrideIds)) {
                intervalsByOverride.computeIfAbsent(interval.getOverride().getId(), ignored -> new ArrayList<>())
                        .add(interval);
            }
        }
        Map<FactoryDateKey, OverrideRule> overridesByDate = new HashMap<>();
        for (FactoryDateOverride override : overrides) {
            List<MinuteInterval> intervals = intervalsByOverride.getOrDefault(override.getId(), List.of()).stream()
                    .map(i -> new MinuteInterval(i.getStartMinute(), i.getEndMinute()))
                    .toList();
            overridesByDate.put(new FactoryDateKey(override.getCalendar().getFactoryId(), override.getLocalDate()),
                    new OverrideRule(override.getKind(), intervals));
        }

        Map<Long, OperatingDecision> result = new LinkedHashMap<>();
        for (Long factoryId : factoryIds) {
            FactoryOperatingCalendar calendar = calendarsByFactory.get(factoryId);
            if (calendar == null) {
                result.put(factoryId, OperatingDecision.legacyAlwaysOpen());
                continue;
            }
            Snapshot snapshot = new Snapshot(factoryId, ZoneId.of(calendar.getTimezoneId()),
                    calendar.getResumeGraceSeconds(), weeklyByFactoryDay, overridesByDate);
            result.put(factoryId, evaluate(snapshot, now));
        }
        return result;
    }

    public OperatingDecision evaluateAlwaysOpenFallback() {
        return OperatingDecision.legacyAlwaysOpen();
    }

    private OperatingDecision evaluate(Snapshot snapshot, Instant now) {
        LocalDate localDate = now.atZone(snapshot.zoneId()).toLocalDate();
        List<InstantInterval> current = snapshot.intervals(localDate);
        InstantInterval active = current.stream().filter(interval -> interval.contains(now)).findFirst().orElse(null);
        if (active == null) {
            return new OperatingDecision(false, false, null, "PLANNED_OFFLINE");
        }

        Instant openSince = active.start();
        LocalDate cursor = localDate;
        for (int i = 0; i < CONTINUITY_LOOKBACK_DAYS && startsAtLocalMidnight(openSince, cursor, snapshot.zoneId()); i++) {
            LocalDate previousDate = cursor.minusDays(1);
            List<InstantInterval> previous = snapshot.intervals(previousDate);
            Instant boundary = openSince;
            InstantInterval connected = previous.stream()
                    .filter(interval -> interval.end().equals(boundary))
                    .findFirst().orElse(null);
            if (connected == null) break;
            openSince = connected.start();
            cursor = previousDate;
        }

        boolean inGrace = snapshot.resumeGraceSeconds() > 0
                && now.isBefore(openSince.plusSeconds(snapshot.resumeGraceSeconds()));
        return new OperatingDecision(true, inGrace, openSince, inGrace ? "RESUME_GRACE" : "SCHEDULED_ACTIVE");
    }

    private static boolean startsAtLocalMidnight(Instant instant, LocalDate date, ZoneId zoneId) {
        return instant.equals(date.atStartOfDay(zoneId).toInstant());
    }

    private static long dayKey(long factoryId, int isoDay) {
        return factoryId * 10L + isoDay;
    }

    private record Snapshot(
            long factoryId,
            ZoneId zoneId,
            int resumeGraceSeconds,
            Map<Long, List<MinuteInterval>> weeklyByFactoryDay,
            Map<FactoryDateKey, OverrideRule> overridesByDate
    ) {
        private List<InstantInterval> intervals(LocalDate date) {
            OverrideRule override = overridesByDate.get(new FactoryDateKey(factoryId, date));
            List<MinuteInterval> minutes;
            if (override != null) {
                minutes = override.kind() == FactoryDateOverride.Kind.CLOSED ? List.of() : override.intervals();
            } else {
                minutes = weeklyByFactoryDay.getOrDefault(dayKey(factoryId, date.getDayOfWeek().getValue()), List.of());
            }
            List<InstantInterval> resolved = minutes.stream()
                    .map(interval -> resolve(date, interval, zoneId))
                    .filter(interval -> interval.end().isAfter(interval.start()))
                    .sorted(Comparator.comparing(InstantInterval::start))
                    .toList();
            return merge(resolved);
        }
    }

    private static InstantInterval resolve(LocalDate date, MinuteInterval interval, ZoneId zoneId) {
        ZonedDateTime start = ZonedDateTime.of(date, toLocalTime(interval.startMinute()), zoneId)
                .withEarlierOffsetAtOverlap();
        ZonedDateTime end = interval.endMinute() == 1440
                ? date.plusDays(1).atStartOfDay(zoneId).withLaterOffsetAtOverlap()
                : ZonedDateTime.of(date, toLocalTime(interval.endMinute()), zoneId).withLaterOffsetAtOverlap();
        return new InstantInterval(start.toInstant(), end.toInstant());
    }

    private static LocalTime toLocalTime(int minute) {
        return LocalTime.of(minute / 60, minute % 60);
    }

    private static List<InstantInterval> merge(List<InstantInterval> source) {
        if (source.isEmpty()) return List.of();
        List<InstantInterval> merged = new ArrayList<>();
        for (InstantInterval next : source) {
            if (merged.isEmpty()) {
                merged.add(next);
                continue;
            }
            InstantInterval last = merged.get(merged.size() - 1);
            if (!next.start().isAfter(last.end())) {
                merged.set(merged.size() - 1,
                        new InstantInterval(last.start(), next.end().isAfter(last.end()) ? next.end() : last.end()));
            } else {
                merged.add(next);
            }
        }
        return List.copyOf(merged);
    }

    private record MinuteInterval(int startMinute, int endMinute) { }
    private record InstantInterval(Instant start, Instant end) {
        private boolean contains(Instant instant) {
            return !instant.isBefore(start) && instant.isBefore(end);
        }
    }
    private record FactoryDateKey(long factoryId, LocalDate date) { }
    private record OverrideRule(FactoryDateOverride.Kind kind, List<MinuteInterval> intervals) { }

    public record OperatingDecision(
            boolean scheduledActive,
            boolean resumeGraceActive,
            Instant activePeriodStart,
            String reason
    ) {
        private static OperatingDecision legacyAlwaysOpen() {
            return new OperatingDecision(true, false, null, "LEGACY_ALWAYS_OPEN");
        }

        public boolean monitoringSuppressed(Instant lastSeenAt) {
            return !scheduledActive || (resumeGraceActive
                    && (lastSeenAt == null || activePeriodStart == null || lastSeenAt.isBefore(activePeriodStart)));
        }
    }
}
