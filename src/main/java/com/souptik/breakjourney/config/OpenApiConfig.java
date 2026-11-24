package com.souptik.breakjourney.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI breakJourneyOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Break Journey Tracker API")
                .version("1.0.0")
                .description("Tracks a Kolkata to Hyderabad break journey (cab, Vande Bharat, auto/cab, Volvo bus), "
                        + "recalculates connection buffers when delays are reported, and streams live updates over SSE."));
    }
}
