package com.devflow.auth;

import com.devflow.domain.user.User;
import com.devflow.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock VerificationCodeService verificationCodeService;
    @Mock JwtUtils jwtUtils;
    @InjectMocks AuthService authService;

    @Test
    void verifyCode_existingUser_returnsToken() {
        var user = new User();
        user.setEmail("test@example.com");
        user.setName("Test User");

        when(verificationCodeService.verify("test@example.com", "123456")).thenReturn(true);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(jwtUtils.generateToken("test@example.com")).thenReturn("jwt-token");

        var result = authService.verifyCode("test@example.com", "123456");

        assertThat(result.token()).isEqualTo("jwt-token");
        assertThat(result.user().email()).isEqualTo("test@example.com");
    }

    @Test
    void verifyCode_invalidCode_throwsException() {
        when(verificationCodeService.verify("test@example.com", "000000")).thenReturn(false);

        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> authService.verifyCode("test@example.com", "000000")
        );
    }
}
