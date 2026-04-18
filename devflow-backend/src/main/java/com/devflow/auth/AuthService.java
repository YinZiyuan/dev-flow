package com.devflow.auth;

import com.devflow.domain.user.User;
import com.devflow.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final VerificationCodeService verificationCodeService;
    private final JwtUtils jwtUtils;

    public record AuthResult(String token, UserDto user) {}
    public record UserDto(String email, String name) {}

    public void sendCode(String email) {
        String code = verificationCodeService.generate(email);
        // In production: send via email service. For now: log it.
        System.out.println("VERIFICATION CODE for " + email + ": " + code);
    }

    @Transactional
    public AuthResult verifyCode(String email, String code) {
        if (!verificationCodeService.verify(email, code)) {
            throw new IllegalArgumentException("Invalid or expired verification code");
        }
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            var u = new User();
            u.setEmail(email);
            u.setName(email.split("@")[0]);
            return userRepository.save(u);
        });
        String token = jwtUtils.generateToken(email);
        return new AuthResult(token, new UserDto(user.getEmail(), user.getName()));
    }

    public UserDto getMe(String email) {
        return userRepository.findByEmail(email)
            .map(u -> new UserDto(u.getEmail(), u.getName()))
            .orElseThrow(() -> new IllegalStateException("User not found"));
    }
}
