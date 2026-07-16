package dev.bugi.sensor.explain.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * explain(Python) 서비스 연동 설정.
 * explain.base-url, explain.enabled 로 주입한다.
 */
@Component
@ConfigurationProperties(prefix = "explain")
@Getter
@Setter
public class ExplainProperties {

    /** explain FastAPI 서비스 주소 */
    private String baseUrl = "http://localhost:23200";

    /** 연동 활성화 여부 (off면 explain 호출을 건너뛴다) */
    private boolean enabled = false;
}
