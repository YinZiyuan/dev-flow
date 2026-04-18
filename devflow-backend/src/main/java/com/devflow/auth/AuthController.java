package com.devflow.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    public record SendCodeRequest(String email) {}
    public record VerifyCodeRequest(String email, String code) {}

    @PostMapping("/send-code")
    public ResponseEntity<Void> sendCode(@RequestBody SendCodeRequest req) {
        authService.sendCode(req.email());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify-code")
    public ResponseEntity<AuthService.AuthResult> verifyCode(@RequestBody VerifyCodeRequest req) {
        return ResponseEntity.ok(authService.verifyCode(req.email(), req.code()));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthService.UserDto> me(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(authService.getMe(email));
    }
}
