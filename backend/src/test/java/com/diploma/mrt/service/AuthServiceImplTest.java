package com.diploma.mrt.service;

import com.diploma.mrt.dto.AuthDtos;
import com.diploma.mrt.entity.Role;
import com.diploma.mrt.entity.User;
import com.diploma.mrt.exception.InvalidCredentialsException;
import com.diploma.mrt.repository.UserRepository;
import com.diploma.mrt.security.JwtService;
import com.diploma.mrt.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {
    @Test
    void registerNormalizesEmailBeforePersistingAndIssuingToken() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JwtService jwtService = mock(JwtService.class);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Secret123!")).thenReturn("hash");
        when(jwtService.generateToken("user@example.com", Role.DOCTOR)).thenReturn("token");

        AuthServiceImpl authService = new AuthServiceImpl(userRepository, passwordEncoder, jwtService, false);
        AuthDtos.AuthResponse response = authService.register(new AuthDtos.RegisterRequest("User@Example.com", "Secret123!"));

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertEquals("user@example.com", savedUser.getValue().getEmail());
        assertEquals("token", response.token());
    }

    @Test
    void loginUsesNormalizedEmailLookup() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JwtService jwtService = mock(JwtService.class);
        User user = new User();
        user.setEmail("doctor@example.com");
        user.setPasswordHash("hash");
        user.setRole(Role.DOCTOR);
        when(userRepository.findByEmail("doctor@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secret123!", "hash")).thenReturn(true);
        when(jwtService.generateToken("doctor@example.com", Role.DOCTOR)).thenReturn("token");

        AuthServiceImpl authService = new AuthServiceImpl(userRepository, passwordEncoder, jwtService, false);
        AuthDtos.AuthResponse response = authService.login(new AuthDtos.LoginRequest("Doctor@Example.com", "Secret123!"));

        assertEquals("token", response.token());
    }

    @Test
    void loginRejectsDemoAccountsWhenFeatureIsDisabled() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JwtService jwtService = mock(JwtService.class);
        User user = new User();
        user.setEmail("admin@demo.local");
        user.setPasswordHash("hash");
        user.setRole(Role.ADMIN);
        when(userRepository.findByEmail("admin@demo.local")).thenReturn(Optional.of(user));

        AuthServiceImpl authService = new AuthServiceImpl(userRepository, passwordEncoder, jwtService, false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(new AuthDtos.LoginRequest("Admin@Demo.Local", "Admin123!")));
    }
}
