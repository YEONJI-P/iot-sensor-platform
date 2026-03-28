package dev.yeon.iotsensorplatform.alert.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DailyAlertCountResponse {
    private String date;
    private Long count;
}
