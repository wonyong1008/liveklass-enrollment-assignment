package com.liveklass.enrollment.common;

import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    /**
     * Jackson 기본 설정은 "price":10000.9처럼 소수 JSON 값이 정수 타입 필드(예: price, capacity)로
     * 들어오면 예외 없이 조용히 소수부를 버린다. 원화(KRW)는 소수 단위가 없어 price를 Long으로
     * 모델링했는데, 이 기본 동작 때문에 클라이언트가 실수로 소수를 보내도 검증에 걸리지 않고
     * 값만 깎여서 저장될 수 있었다(실제 MySQL로 확인: 10000.9 요청 -> 응답은 10000). 정수 타입
     * 전체에 대해 소수 입력을 명시적으로 거부하도록 강제한다.
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer integerCoercionCustomizer() {
        return builder -> builder.postConfigurer(objectMapper ->
                objectMapper.coercionConfigFor(LogicalType.Integer)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail));
    }
}
