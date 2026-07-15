package dev.yeon.iotsensorplatform.global.config;

import dev.yeon.iotsensorplatform.admin.service.AdminService;
import dev.yeon.iotsensorplatform.admin.service.ZoneService;
import dev.yeon.iotsensorplatform.admin.service.FactoryService;
import dev.yeon.iotsensorplatform.alert.service.AlertService;
import dev.yeon.iotsensorplatform.auth.filter.JwtFilter;
import dev.yeon.iotsensorplatform.auth.service.AuthService;
import dev.yeon.iotsensorplatform.auth.util.JwtUtil;
import dev.yeon.iotsensorplatform.device.service.DeviceService;
import dev.yeon.iotsensorplatform.global.security.CustomAccessDeniedHandler;
import dev.yeon.iotsensorplatform.global.security.CustomAuthenticationEntryPoint;
import dev.yeon.iotsensorplatform.global.service.AccessControlService;
import dev.yeon.iotsensorplatform.sensordata.service.SensorDataService;
import dev.yeon.iotsensorplatform.sse.SseService;
import dev.yeon.iotsensorplatform.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    // -- 공개 endpoint --
    @Test
    void post_sensor_data_no_auth() throws Exception {
        mockMvc.perform(post("/sensor-data")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"deviceId\":1,\"value\":50.0}"))
                .andExpect(status().is(not(401)));
    }

    // -- 보호 endpoint --
    @Test
    void get_sensor_data_no_auth() throws Exception {
        mockMvc.perform(get("/sensor-data"))
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
