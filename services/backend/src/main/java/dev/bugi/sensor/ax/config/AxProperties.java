package dev.bugi.sensor.ax.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AX(Python) 서비스 연동 설정.
 * ax.base-url, ax.enabled 로 주입한다.
 */
@Component
@ConfigurationProperties(prefix = "ax")
@Getter
@Setter
public class AxProperties {

    /** AX FastAPI 서비스 주소 */
    private String baseUrl = "http://localhost:23200";

    /** 연동 활성화 여부 (off면 AX 호출을 건너뛴다) */
    private boolean enabled = false;
}
