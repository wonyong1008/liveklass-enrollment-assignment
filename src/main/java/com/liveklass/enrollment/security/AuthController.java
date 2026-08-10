package com.liveklass.enrollment.security;

import com.liveklass.enrollment.security.dto.TokenRequest;
import com.liveklass.enrollment.security.dto.TokenResponse;
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
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/token")
    public TokenResponse issueToken(@Valid @RequestBody TokenRequest request) {
        String token = jwtTokenProvider.createToken(request.userId());
        return TokenResponse.of(token);
    }
}
