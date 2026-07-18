package dev.bugi.sensor.global.config;

import dev.bugi.sensor.admin.service.AdminService;
import dev.bugi.sensor.admin.service.ZoneService;
import dev.bugi.sensor.admin.service.FactoryService;
import dev.bugi.sensor.alert.service.AlertService;
import dev.bugi.sensor.auth.service.AuthService;
import dev.bugi.sensor.auth.util.JwtUtil;
import dev.bugi.sensor.dashboard.service.DashboardOverviewService;
import dev.bugi.sensor.device.service.ChannelService;
import dev.bugi.sensor.device.service.DeviceService;
import dev.bugi.sensor.global.security.CustomAccessDeniedHandler;
import dev.bugi.sensor.global.security.CustomAuthenticationEntryPoint;
import dev.bugi.sensor.global.service.AccessControlService;
import dev.bugi.sensor.sensordata.dto.BatchIngestResponse;
import dev.bugi.sensor.sensordata.dto.BatchIngestResult;
import dev.bugi.sensor.sensordata.failure.FailedReadingRepository;
import dev.bugi.sensor.sensordata.service.SensorDataService;
import dev.bugi.sensor.sse.SseService;
import dev.bugi.sensor.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@Import({
        SecurityConfig.class,
        CustomAuthenticationEntryPoint.class,
        CustomAccessDeniedHandler.class
})
public class SecurityConfigTest {
    private static final String INGEST_KEY = "test-ingest-key";
    private static final String VALID_BODY =
            "{\"deviceCode\":\"CMAPSS-U1\",\"measurements\":{\"s4\":100.0}}";

    @Autowired
    MockMvc mockMvc;
    @MockitoBean
    SensorDataService sensorDataService;
    @MockitoBean
    ChannelService channelService;
    @MockitoBean
    AuthService authService;
    @MockitoBean
    DeviceService deviceService;
    @MockitoBean
    DashboardOverviewService dashboardOverviewService;
    @MockitoBean
    AlertService alertService;
    @MockitoBean
    AdminService adminService;
    @MockitoBean
    ZoneService zoneService;
    @MockitoBean
    FactoryService factoryService;
    @MockitoBean
    AccessControlService accessControlService;
    @MockitoBean
    SseService sseService;
    @MockitoBean
    UserRepository userRepository;
    @MockitoBean
    JwtUtil jwtUtil;
    // GlobalExceptionHandler(@RestControllerAdvice)가 요구하는 의존.
    @MockitoBean
    FailedReadingRepository failedReadingRepository;

    // -- ingest 공유 키 endpoint(C1: deviceCode + measurements) --
    @Test
    void post_sensor_data_with_valid_ingest_key_returns_200() throws Exception {
        given(sensorDataService.receive(any())).willReturn(result(BatchIngestResult.Outcome.SAVED));

        mockMvc.perform(post("/sensor-data")
                .header("X-Ingest-Key", INGEST_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void post_sensor_data_without_ingest_key_returns_contract_401() throws Exception {
        mockMvc.perform(post("/sensor-data")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("유효한 센서 수신 키가 필요합니다."));
        verifyNoInteractions(sensorDataService);
    }

    @Test
    void post_sensor_data_with_invalid_ingest_key_returns_contract_401() throws Exception {
        mockMvc.perform(post("/sensor-data")
                .header("X-Ingest-Key", "wrong-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("유효한 센서 수신 키가 필요합니다."));
        verifyNoInteractions(sensorDataService);
    }

    @Test
    void post_sensor_data_with_user_jwt_only_returns_401() throws Exception {
        given(jwtUtil.validateAccessToken("user-token")).willReturn(true);
        given(jwtUtil.getEmployeeId("user-token")).willReturn("member-1");
        given(jwtUtil.getRole("user-token")).willReturn("MEMBER");

        mockMvc.perform(post("/sensor-data")
                .header("Authorization", "Bearer user-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("유효한 센서 수신 키가 필요합니다."));
        verifyNoInteractions(sensorDataService);
    }

    // -- C2 계약: 잘못된 수신 요청은 400(500 아님) + 실패 적재 --
    @Test
    void post_sensor_data_invalid_returns_400_and_records_failure() throws Exception {
        mockMvc.perform(post("/sensor-data")
                .header("X-Ingest-Key", INGEST_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"deviceCode\":\"\",\"measurements\":{}}"))
                .andExpect(status().isBadRequest());
        verify(failedReadingRepository).save(any());
    }

    @Test
    void post_sensor_data_device_not_found_still_returns_404() throws Exception {
        given(sensorDataService.receive(any())).willReturn(result(BatchIngestResult.Outcome.DEVICE_NOT_FOUND));

        mockMvc.perform(post("/sensor-data")
                .header("X-Ingest-Key", INGEST_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_sensor_data_no_known_channels_still_returns_422() throws Exception {
        given(sensorDataService.receive(any())).willReturn(result(BatchIngestResult.Outcome.NO_KNOWN_CHANNELS));

        mockMvc.perform(post("/sensor-data")
                .header("X-Ingest-Key", INGEST_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
                .andExpect(status().isUnprocessableEntity());
    }

    // -- 보호 endpoint --
    @Test
    void get_sensor_data_no_auth() throws Exception {
        mockMvc.perform(get("/sensor-data"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_channels_no_auth() throws Exception {
        mockMvc.perform(get("/channels"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_dashboard_overview_no_auth() throws Exception {
        mockMvc.perform(get("/dashboard/overview"))
                .andExpect(status().isUnauthorized());
    }

    // -- 역할 제한

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void get_admin_factory_with_system_admin() throws Exception {
        mockMvc.perform(get("/admin/factories"))
                .andExpect(status().is(not(403)));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void get_channels_with_viewer_ok() throws Exception {
        mockMvc.perform(get("/channels"))
                .andExpect(status().is(not(403)));
    }

    @Test
    @WithMockUser(roles = "FACTORY_ADMIN")
    void get_channels_factory_admin_forbidden() throws Exception {
        mockMvc.perform(get("/channels"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MEMBER")
    void put_channel_member_ok() throws Exception {
        mockMvc.perform(put("/channels/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().is(not(403)));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void put_channel_viewer_forbidden() throws Exception {
        mockMvc.perform(put("/channels/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MEMBER")
    void post_device_channel_member_ok() throws Exception {
        mockMvc.perform(post("/devices/1/channels")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"s99\"}"))
                .andExpect(status().is(not(403)));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void get_sensor_data_with_viewer() throws Exception {
        mockMvc.perform(get("/sensor-data"))
                .andExpect(status().is(not(403)));
    }

    @Test
    @WithMockUser(roles = "FACTORY_ADMIN")
    void get_sensor_data_org_admin_forbidden() throws Exception {
        mockMvc.perform(get("/sensor-data"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "FACTORY_ADMIN")
    void get_admin_zones_org_admin_ok() throws Exception {
        mockMvc.perform(get("/admin/zones"))
                .andExpect(status().is(not(403)));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void get_admin_zones_viewer_forbidden() throws Exception {
        mockMvc.perform(get("/admin/zones"))
                .andExpect(status().isForbidden());
    }

    private BatchIngestResult result(BatchIngestResult.Outcome outcome) {
        Instant now = Instant.parse("2026-07-19T00:00:00Z");
        BatchIngestResponse response = new BatchIngestResponse(
                outcome == BatchIngestResult.Outcome.SAVED ? 1L : null,
                outcome == BatchIngestResult.Outcome.DEVICE_NOT_FOUND ? null : 1L,
                "CMAPSS-U1",
                outcome == BatchIngestResult.Outcome.DEVICE_NOT_FOUND ? null : now,
                now,
                outcome == BatchIngestResult.Outcome.SAVED ? 1 : 0,
                List.of()
        );
        return new BatchIngestResult(outcome, response);
    }
}
