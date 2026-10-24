package sit.int221.backend.controllers;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sit.int221.backend.dtos.LoginRequestDto;
import sit.int221.backend.dtos.LoginResponseDto;
import sit.int221.backend.services.AuthService;
import sit.int221.backend.services.TokenBlacklistService;

import java.util.Map;

@RestController
@RequestMapping("/v2/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @Valid @RequestBody LoginRequestDto dto,
            HttpServletResponse response
    ) {
        // ✅ delegate ไป service
        LoginResponseDto result = authService.login(dto);

        // ✅ set refresh token เป็น HttpOnly cookie
        ResponseCookie cookie = ResponseCookie.from("refresh_token", result.getRefreshToken())
                .httpOnly(true)
                .secure(false) // 👉 deploy จริงใช้ true
                .path("/")     // 👉 ทุก request จะส่ง cookie นี้ไป
                .maxAge(24 * 60 * 60) // 24 ชั่วโมง
                .sameSite("Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        // ✅ return access token อย่างเดียว
        Map<String, Object> body = Map.of("access_token", result.getAccessToken());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshToken(
            @CookieValue(value = "refresh_token", required = false) String refreshToken
    ) {
        if (refreshToken == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "MISSING_REFRESH_TOKEN",
                    "message", "Refresh token is required"
            ));
        }

        try {
            String newAccessToken = authService.refreshAccessToken(refreshToken);
            return ResponseEntity.ok(Map.of("access_token", newAccessToken));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "INVALID_OR_EXPIRED_REFRESH_TOKEN",
                    "message", e.getMessage()
            ));
        }
    }
    private final TokenBlacklistService tokenBlacklistService;
    @PostMapping("/signout")
    public ResponseEntity<Void> signout(
            @RequestHeader("Authorization") String authHeader,
            HttpServletResponse response) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            tokenBlacklistService.add(token);
        }

        ResponseCookie deleteCookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, deleteCookie.toString());

        return ResponseEntity.noContent().build();
    }


}
