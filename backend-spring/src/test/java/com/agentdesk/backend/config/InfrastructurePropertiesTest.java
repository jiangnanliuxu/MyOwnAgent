package com.agentdesk.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "agent-desk.infrastructure.enabled=true",
        "agent-desk.infrastructure.postgres.host=db.internal",
        "agent-desk.infrastructure.postgres.port=15432",
        "agent-desk.infrastructure.postgres.database=agentdesk_test",
        "agent-desk.infrastructure.postgres.username=agentdesk",
        "agent-desk.infrastructure.redis.host=redis.internal",
        "agent-desk.infrastructure.redis.port=16379",
        "agent-desk.infrastructure.minio.endpoint=http://minio.internal:9000",
        "agent-desk.infrastructure.minio.bucket=agent-desk-test",
        "agent-desk.infrastructure.minio.access-key=minio-access",
        "agent-desk.infrastructure.minio.secret-key=minio-secret",
        "agent-desk.infrastructure.milvus.enabled=false",
        "agent-desk.infrastructure.milvus.endpoint=http://milvus.internal:19530"
})
class InfrastructurePropertiesTest {

    @Autowired
    private InfrastructureProperties properties;

    @Test
    void bindsInfrastructureConfiguration() {
        assertThat(properties.enabled()).isTrue();
        assertThat(properties.postgres().host()).isEqualTo("db.internal");
        assertThat(properties.postgres().port()).isEqualTo(15432);
        assertThat(properties.postgres().database()).isEqualTo("agentdesk_test");
        assertThat(properties.redis().host()).isEqualTo("redis.internal");
        assertThat(properties.redis().port()).isEqualTo(16379);
        assertThat(properties.minio().endpoint()).isEqualTo("http://minio.internal:9000");
        assertThat(properties.minio().bucket()).isEqualTo("agent-desk-test");
        assertThat(properties.minio().accessKey()).isEqualTo("minio-access");
        assertThat(properties.minio().secretKey()).isEqualTo("minio-secret");
        assertThat(properties.milvus().enabled()).isFalse();
        assertThat(properties.milvus().endpoint()).isEqualTo("http://milvus.internal:19530");
    }
}
