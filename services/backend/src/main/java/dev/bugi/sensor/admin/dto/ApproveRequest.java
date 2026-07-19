package dev.bugi.sensor.admin.dto;

import dev.bugi.sensor.user.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

@Schema(description = "가입 승인 요청 — 부여할 역할과 배정할 구역")
public record ApproveRequest(

        @NotNull(message = "부여할 역할은 필수예요")
        @Schema(description = "부여할 역할. FACTORY_ADMIN 승인자는 VIEWER/MEMBER, SYSTEM_ADMIN 승인자는 FACTORY_ADMIN까지", example = "MEMBER")
        Role role,

        @NotNull(message = "배정할 공장은 필수예요")
        @Positive(message = "공장 ID는 1 이상이어야 해요")
        @Schema(description = "배정할 공장 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Long factoryId,

        @Schema(description = "배정할 구역 id 목록 (선택). 모든 구역은 선택한 공장 소속이어야 함")
        List<Long> zoneIds
) {
    public List<Long> zoneIds() {
        return zoneIds == null ? List.of() : zoneIds;
    }
}
