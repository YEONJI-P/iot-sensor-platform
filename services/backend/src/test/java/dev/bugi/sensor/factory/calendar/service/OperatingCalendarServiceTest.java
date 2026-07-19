package dev.bugi.sensor.factory.calendar.service;

import dev.bugi.sensor.factory.calendar.entity.FactoryDateOverride;
import dev.bugi.sensor.factory.calendar.entity.FactoryDateOverrideInterval;
import dev.bugi.sensor.factory.calendar.entity.FactoryOperatingCalendar;
import dev.bugi.sensor.factory.calendar.entity.FactoryWeeklyInterval;
import dev.bugi.sensor.factory.calendar.repository.FactoryDateOverrideIntervalRepository;
import dev.bugi.sensor.factory.calendar.repository.FactoryDateOverrideRepository;
import dev.bugi.sensor.factory.calendar.repository.FactoryOperatingCalendarRepository;
import dev.bugi.sensor.factory.calendar.repository.FactoryWeeklyIntervalRepository;
import dev.bugi.sensor.factory.calendar.service.OperatingCalendarService.OperatingDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperatingCalendarServiceTest {

    @Mock FactoryOperatingCalendarRepository calendarRepository;
    @Mock FactoryWeeklyIntervalRepository weeklyRepository;
    @Mock FactoryDateOverrideRepository overrideRepository;
    @Mock FactoryDateOverrideIntervalRepository overrideIntervalRepository;

    OperatingCalendarService service;

    @BeforeEach
    void setUp() {
        service = new OperatingCalendarService(calendarRepository, weeklyRepository,
                overrideRepository, overrideIntervalRepository);
        lenient().when(overrideRepository.findAllInRange(anyList(), any(), any())).thenReturn(List.of());
    }

    @Test
    void 캘린더가_없으면_24시간_감시_fallback이다() {
        when(calendarRepository.findAllWithFactoryByFactoryIdIn(List.of(1L))).thenReturn(List.of());
        when(weeklyRepository.findAllByFactoryIdIn(List.of(1L))).thenReturn(List.of());

        OperatingDecision decision = evaluate(Instant.parse("2026-07-20T03:00:00Z"));

        assertThat(decision.scheduledActive()).isTrue();
        assertThat(decision.resumeGraceActive()).isFalse();
        assertThat(decision.reason()).isEqualTo("LEGACY_ALWAYS_OPEN");
    }

    @Test
    void 복수구간은_시작포함_종료제외이고_재개유예를_계산한다() {
        calendar("Asia/Seoul", 300);
        weekly(1, 480, 720);
        weekly(1, 780, 1080);

        OperatingDecision atStart = evaluate(Instant.parse("2026-07-19T23:00:00Z")); // 월 08:00
        OperatingDecision atEnd = evaluate(Instant.parse("2026-07-20T03:00:00Z"));   // 월 12:00
        OperatingDecision afternoon = evaluate(Instant.parse("2026-07-20T04:03:00Z")); // 월 13:03

        assertThat(atStart.scheduledActive()).isTrue();
        assertThat(atStart.resumeGraceActive()).isTrue();
        assertThat(atStart.activePeriodStart()).isEqualTo(Instant.parse("2026-07-19T23:00:00Z"));
        assertThat(atEnd.scheduledActive()).isFalse();
        assertThat(afternoon.scheduledActive()).isTrue();
        assertThat(afternoon.resumeGraceActive()).isTrue();
    }

    @Test
    void CLOSED와_OPEN_날짜예외가_주간표를_완전히_대체한다() {
        FactoryOperatingCalendar calendar = calendar("Asia/Seoul", 0);
        weekly(1, 480, 1080);
        FactoryDateOverride closed = override(calendar, 10L, LocalDate.of(2026, 7, 20), FactoryDateOverride.Kind.CLOSED);
        FactoryDateOverride open = override(calendar, 11L, LocalDate.of(2026, 7, 21), FactoryDateOverride.Kind.OPEN);
        when(overrideRepository.findAllInRange(anyList(), any(), any())).thenReturn(List.of(closed, open));
        FactoryDateOverrideInterval special = overrideInterval(open, 600, 660);
        when(overrideIntervalRepository.findAllByOverrideIdIn(List.of(10L, 11L))).thenReturn(List.of(special));

        assertThat(evaluate(Instant.parse("2026-07-20T00:00:00Z")).scheduledActive()).isFalse(); // 월 09:00
        assertThat(evaluate(Instant.parse("2026-07-21T01:30:00Z")).scheduledActive()).isTrue();  // 화 10:30
        assertThat(evaluate(Instant.parse("2026-07-21T00:00:00Z")).scheduledActive()).isFalse(); // 화 09:00
    }

    @Test
    void 매일_24시간_일정은_자정마다_새_유예를_시작하지_않는다() {
        calendar("Asia/Seoul", 86400);
        for (int day = 1; day <= 7; day++) weekly(day, 0, 1440);

        OperatingDecision decision = evaluate(Instant.parse("2026-07-20T15:01:00Z")); // 화 00:01

        assertThat(decision.scheduledActive()).isTrue();
        assertThat(decision.resumeGraceActive()).isFalse();
    }

    @Test
    void DST_overlap의_두_번째_시각도_같은_운영구간이다() {
        calendar("America/New_York", 0);
        weekly(7, 60, 180); // 2026-11-01 일요일 01:00~03:00

        OperatingDecision first = evaluate(Instant.parse("2026-11-01T05:30:00Z"));
        OperatingDecision second = evaluate(Instant.parse("2026-11-01T06:30:00Z"));

        assertThat(first.scheduledActive()).isTrue();
        assertThat(second.scheduledActive()).isTrue();
        assertThat(second.activePeriodStart()).isEqualTo(Instant.parse("2026-11-01T05:00:00Z"));
    }

    @Test
    void 유예중_운영시작후_수신이_있으면_suppression을_즉시_해제한다() {
        calendar("UTC", 300);
        weekly(1, 480, 1080);
        OperatingDecision decision = evaluate(Instant.parse("2026-07-20T08:01:00Z"));

        assertThat(decision.monitoringSuppressed(Instant.parse("2026-07-20T07:59:00Z"))).isTrue();
        assertThat(decision.monitoringSuppressed(Instant.parse("2026-07-20T08:00:30Z"))).isFalse();
    }

    private OperatingDecision evaluate(Instant now) {
        Map<Long, OperatingDecision> decisions = service.evaluate(List.of(1L), now);
        return decisions.get(1L);
    }

    private FactoryOperatingCalendar calendar(String timezone, int grace) {
        FactoryOperatingCalendar calendar = mock(FactoryOperatingCalendar.class);
        when(calendar.getFactoryId()).thenReturn(1L);
        when(calendar.getTimezoneId()).thenReturn(timezone);
        when(calendar.getResumeGraceSeconds()).thenReturn(grace);
        when(calendarRepository.findAllWithFactoryByFactoryIdIn(List.of(1L))).thenReturn(List.of(calendar));
        return calendar;
    }

    private void weekly(int isoDay, int start, int end) {
        List<FactoryWeeklyInterval> current = new java.util.ArrayList<>(
                weeklyRepository.findAllByFactoryIdIn(List.of(1L)) == null
                        ? List.of() : weeklyRepository.findAllByFactoryIdIn(List.of(1L)));
        FactoryOperatingCalendar calendar = calendarRepository.findAllWithFactoryByFactoryIdIn(List.of(1L)).get(0);
        FactoryWeeklyInterval interval = mock(FactoryWeeklyInterval.class);
        when(interval.getCalendar()).thenReturn(calendar);
        when(interval.getIsoDay()).thenReturn((short) isoDay);
        when(interval.getStartMinute()).thenReturn((short) start);
        when(interval.getEndMinute()).thenReturn((short) end);
        current.add(interval);
        when(weeklyRepository.findAllByFactoryIdIn(List.of(1L))).thenReturn(current);
    }

    private FactoryDateOverride override(FactoryOperatingCalendar calendar, long id, LocalDate date,
                                         FactoryDateOverride.Kind kind) {
        FactoryDateOverride override = mock(FactoryDateOverride.class);
        when(override.getId()).thenReturn(id);
        when(override.getCalendar()).thenReturn(calendar);
        when(override.getLocalDate()).thenReturn(date);
        when(override.getKind()).thenReturn(kind);
        return override;
    }

    private FactoryDateOverrideInterval overrideInterval(FactoryDateOverride override, int start, int end) {
        FactoryDateOverrideInterval interval = mock(FactoryDateOverrideInterval.class);
        when(interval.getOverride()).thenReturn(override);
        when(interval.getStartMinute()).thenReturn((short) start);
        when(interval.getEndMinute()).thenReturn((short) end);
        return interval;
    }
}
