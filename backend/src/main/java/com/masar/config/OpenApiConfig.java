package com.masar.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI documentation at /swagger-ui.html, with a "Bearer token" button to try protected endpoints.
 * توثيق Swagger على /swagger-ui.html مع زر "Bearer token" لتجربة الروابط المحمية.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI masarOpenApi() {
        String scheme = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Masar Pro API")
                        .version("1.0.0")
                        .description("Home-services REST API: public booking and tracking, quotes, scheduling and field jobs"))
                // Every endpoint requires the JWT unless security config marks it public.
                // كل رابط يحتاج JWT إلا اللي حددناه عامًا في إعداد الأمان.
                .addSecurityItem(new SecurityRequirement().addList(scheme))
                .components(new Components().addSecuritySchemes(scheme, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}
