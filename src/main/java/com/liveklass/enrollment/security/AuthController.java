package com.liveklass.enrollment.security;

import com.liveklass.enrollment.security.dto.TokenRequest;
import com.liveklass.enrollment.security.dto.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 과제 범위에 별도 회원가입/비밀번호 체계가 없어, userId만으로 토큰을 발급하는
 * 간이 로그인으로 대체한다. 실제 서비스라면 이 자리에 자격 증명 검증이 들어간다.
 */
@Tag(name = "인증", description = "JWT 발급 (간이 로그인)")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;

    @Operation(
            summary = "토큰 발급",
            description = "userId만으로 JWT를 발급한다(별도 자격 증명 검증 없음, README '요구사항 해석 및 가정' 참고)."
    )
    @SecurityRequirements
    @PostMapping("/token")
    public TokenResponse issueToken(@Valid @RequestBody TokenRequest request) {
        String token = jwtTokenProvider.createToken(request.userId());
        return TokenResponse.of(token);
    }
}
