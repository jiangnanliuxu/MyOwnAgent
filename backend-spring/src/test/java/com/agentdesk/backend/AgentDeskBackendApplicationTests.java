package com.agentdesk.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AgentDeskBackendApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    @Test
    void contextLoads() {
    }

    @Test
    @DisplayName("default profile does not create external infrastructure connections")
    void defaultProfileDoesNotCreateExternalInfrastructureConnections() {
        assumeFalse(environment.acceptsProfiles(Profiles.of("dev")));
        assertThat(applicationContext.getBeansOfType(DataSource.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(RedisConnectionFactory.class)).isEmpty();
    }
}
