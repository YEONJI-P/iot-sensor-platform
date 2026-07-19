package dev.bugi.sensor.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import dev.bugi.sensor.factory.calendar.entity.FactoryDateOverride;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

public final class FactoryCalendarDtos {
    private FactoryCalendarDtos() { }

    public record Summary(
            Long factoryId,
            String factoryName,
            String timezone,
            int resumeGraceSeconds,
            long revision
    ) { }

    public record Interval(
            DayOfWeek dayOfWeek,
            String start,
            String end
    ) { }

    public record OverrideInterval(String start, String end) { }

    public record DateOverride(
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
            FactoryDateOverride.Kind kind,
            List<OverrideInterval> intervals
    ) { }

    public record ReplaceRequest(
            String timezone,
            Integer resumeGraceSeconds,
            Long revision,
            List<Interval> weeklyIntervals,
            List<DateOverride> dateOverrides
    ) { }

    public record Detail(
            Long factoryId,
            String factoryName,
            String timezone,
            int resumeGraceSeconds,
            long revision,
            List<Interval> weeklyIntervals,
            List<DateOverride> dateOverrides
    ) { }
}
