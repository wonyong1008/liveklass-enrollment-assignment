package com.liveklass.enrollment.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "라이브클래스 수강 신청 API",
                version = "v1",
                description = "강의 개설/모집과 수강 신청/결제확정/취소를 처리하는 API입니다. "
                        + "`/api/auth/token`으로 발급받은 JWT를 Authorize 버튼에 등록하면 나머지 API를 바로 호출해볼 수 있습니다."
        ),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {
}
