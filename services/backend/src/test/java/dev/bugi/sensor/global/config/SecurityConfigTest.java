package dev.bugi.sensor.global.config;

import dev.bugi.sensor.admin.service.AdminService;
import dev.bugi.sensor.admin.service.ZoneService;
import dev.bugi.sensor.admin.service.FactoryService;
import dev.bugi.sensor.alert.service.AlertService;
import dev.bugi.sensor.auth.service.AuthService;
import dev.bugi.sensor.auth.util.JwtUtil;
import dev.bugi.sensor.device.service.ChannelService;
import dev.bugi.sensor.device.service.DeviceService;
import dev.bugi.sensor.global.security.CustomAccessDeniedHandler;
import dev.bugi.sensor.global.security.CustomAuthenticationEntryPoint;
import dev.bugi.sensor.global.service.AccessControlService;
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

import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@Import({
        SecurityConfig.class,
        CustomAuthenticationEntryPoint.class,
        CustomAccessDeniedHandler.class
})
public class SecurityConfigTest {
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

    // -- 공개 endpoint(C1: deviceCode + measurements) --
    @Test
    void post_sensor_data_no_auth() throws Exception {
        mockMvc.perform(post("/sensor-data")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"deviceCode\":\"CMAPSS-U1\",\"measurements\":{\"s4\":100.0}}"))
                .andExpect(status().is(not(401)));
    }

    // -- C2 계약: 잘못된 수신 요청은 400(500 아님) + 실패 적재 --
    @Test
    void post_sensor_data_invalid_returns_400_and_records_failure() throws Exception {
        mockMvc.perform(post("/sensor-data")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"deviceCode\":\"\",\"measurements\":{}}"))
                .andExpect(status().isBadRequest());
        verify(failedReadingRepository).save(any());
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
}
