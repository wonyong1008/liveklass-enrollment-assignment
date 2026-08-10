package com.liveklass.enrollment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /**
     * 서비스 로직에서 LocalDateTime.now()를 직접 호출하지 않고 이 빈을 주입받아 사용한다.
     * 테스트에서 Clock.fixed(...)로 교체하면 취소 가능 기간 경계값 같은 시간 의존 로직을 결정적으로 검증할 수 있다.
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
