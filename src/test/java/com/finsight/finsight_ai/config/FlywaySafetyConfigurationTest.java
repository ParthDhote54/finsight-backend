package com.finsight.finsight_ai.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FlywaySafetyConfigurationTest {

    @Test
    void sharedConfigurationDisablesDestructiveFlywayBehavior() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            assertNotNull(input);
            properties.load(input);
        }

        assertEquals("true", properties.getProperty("spring.flyway.clean-disabled"));
        assertEquals("false", properties.getProperty("spring.flyway.clean-on-validation-error"));
    }
}
