package sit.int221.backend.services;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import sit.int221.backend.dtos.LoginRequestDto;
import sit.int221.backend.dtos.LoginResponseDto;
import sit.int221.backend.entities.User;
import sit.int221.backend.repositories.UserRepository;
import sit.int221.backend.security.JwtUtil;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public LoginResponseDto login(LoginRequestDto dto) {
        var user = userRepository.findByEmailIgnoreCase(dto.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(dto.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            // ✅ Custom message ที่ต้องการ
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You need to activate your account before signing in."
            );
        }

        // ✅ claims (optional เอาไป embed ใน JWT)
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", user.getId());
        claims.put("nickname", user.getNickName());
        claims.put("email", user.getEmail());
        claims.put("role", user.getUserType());

        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);

        return LoginResponseDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getNickName())
                .role(user.getUserType())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Transactional(readOnly = true)
    public String refreshAccessToken(String refreshToken) {
        Claims claims = jwtUtil.extractAllClaims(refreshToken);

        var user = userRepository.findById(Integer.parseInt(claims.getSubject()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        return jwtUtil.generateAccessToken(user);
    }

}

