package com.trading.authservice.service;

import com.trading.authservice.dtos.AuthResponse;
import com.trading.authservice.dtos.LoginRequest;
import com.trading.authservice.dtos.RegisterRequest;
import com.trading.authservice.entity.Role;
import com.trading.authservice.entity.RoleName;
import com.trading.authservice.entity.User;
import com.trading.authservice.exception.ApiException;
import com.trading.authservice.model.UserPrincipal;
import com.trading.authservice.repository.RoleRepository;
import com.trading.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    // ✅ Register a new user
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ApiException("Username already exists");
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new ApiException("Email already exists");
        }

        Role defaultRole = roleRepository.findByName(RoleName.ROLE_USER)
                .orElseThrow(() -> new RuntimeException("Default role not found"));

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRoles(Set.of(defaultRole));

        userRepository.save(user);

        UserPrincipal userPrincipal = new UserPrincipal(user);
        String token = jwtService.generateToken(userPrincipal);

        return new AuthResponse(token, user.getId());
    }

    // ✅ Login using LoginRequest
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(), request.password())
        );

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        String token = jwtService.generateToken(userPrincipal);

        return new AuthResponse(token, userPrincipal.getId());
    }
}
