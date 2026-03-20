package com.diploma.mrt.service.impl;

import com.diploma.mrt.dto.AuthDtos;
import com.diploma.mrt.exception.BadRequestException;
import com.diploma.mrt.exception.ConflictException;
import com.diploma.mrt.entity.Role;
import com.diploma.mrt.entity.User;
import com.diploma.mrt.exception.InvalidCredentialsException;
import com.diploma.mrt.repository.UserRepository;
import com.diploma.mrt.security.JwtService;
import com.diploma.mrt.service.AuthService;
import com.diploma.mrt.util.EmailNormalizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final boolean demoUsersEnabled;

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                           @Value("${app.demo-users-enabled:false}") boolean demoUsersEnabled) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.demoUsersEnabled = demoUsersEnabled;
    }

    @Override
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        String normalizedEmail = EmailNormalizer.normalize(request.email());
        if (isReservedDemoEmail(normalizedEmail) && !demoUsersEnabled) {
            throw new BadRequestException("Demo accounts are disabled in this environment");
        }
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new ConflictException("User already exists");
        }
        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.DOCTOR);
        user.setCreatedAt(Instant.now());
        userRepository.save(user);
        return new AuthDtos.AuthResponse(jwtService.generateToken(user.getEmail(), user.getRole()));
    }

    @Override
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        String normalizedEmail = EmailNormalizer.normalize(request.email());
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));
        if (isReservedDemoEmail(user.getEmail()) && !demoUsersEnabled) {
            throw new InvalidCredentialsException("Invalid credentials");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid credentials");
        }
        return new AuthDtos.AuthResponse(jwtService.generateToken(user.getEmail(), user.getRole()));
    }

    private boolean isReservedDemoEmail(String email) {
        return email != null && email.endsWith("@demo.local");
    }
}
