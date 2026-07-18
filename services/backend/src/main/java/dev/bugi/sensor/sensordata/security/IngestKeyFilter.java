package dev.bugi.sensor.sensordata.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

public class IngestKeyFilter extends OncePerRequestFilter {

    static final String HEADER_NAME = "X-Ingest-Key";
    private static final String INGEST_PATH = "/sensor-data";
    private static final String UNAUTHORIZED_RESPONSE =
            "{\"error\":\"UNAUTHORIZED\",\"message\":\"유효한 센서 수신 키가 필요합니다.\"}";

    private final byte[] expectedKey;

    public IngestKeyFilter(String ingestApiKey) {
        if (ingestApiKey == null || ingestApiKey.isBlank()) {
            throw new IllegalStateException("INGEST_API_KEY 환경변수가 필요합니다.");
        }
        this.expectedKey = ingestApiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !HttpMethod.POST.matches(request.getMethod())
                || !INGEST_PATH.equals(path);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String providedKey = request.getHeader(HEADER_NAME);
        if (!matches(providedKey)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(UNAUTHORIZED_RESPONSE);
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "sensor-ingest",
                        null,
                        List.of(new SimpleGrantedAuthority("INGEST"))
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private boolean matches(String providedKey) {
        if (providedKey == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedKey, providedKey.getBytes(StandardCharsets.UTF_8));
    }
}
