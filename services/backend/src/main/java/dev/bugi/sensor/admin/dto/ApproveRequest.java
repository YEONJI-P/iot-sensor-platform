package dev.bugi.sensor.admin.dto;

import dev.bugi.sensor.user.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "가입 승인 요청 — 부여할 역할과 배정할 구역")
public record ApproveRequest(

        @NotNull(message = "부여할 역할은 필수예요")
        @Schema(description = "부여할 역할. FACTORY_ADMIN 승인자는 VIEWER/MEMBER, SYSTEM_ADMIN 승인자는 FACTORY_ADMIN까지", example = "MEMBER")
        Role role,

        @Schema(description = "배정할 구역 id 목록 (선택). 여러 구역은 같은 공장이어야 함")
        List<Long> zoneIds
) {
    public List<Long> zoneIds() {
        return zoneIds == null ? List.of() : zoneIds;
    }
}
