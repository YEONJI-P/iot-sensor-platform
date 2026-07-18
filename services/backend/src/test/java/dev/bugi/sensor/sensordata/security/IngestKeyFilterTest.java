package dev.bugi.sensor.sensordata.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IngestKeyFilterTest {

    @Test
    void blank_ingest_key_fails_fast() {
        assertThatThrownBy(() -> new IngestKeyFilter("   "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INGEST_API_KEY");
    }
}
