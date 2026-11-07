package com.DatShop.service;

import com.DatShop.dto.auth.AuthResponse;
import com.DatShop.dto.auth.CurrentUserResponse;
import com.DatShop.dto.auth.LoginRequest;
import com.DatShop.dto.auth.RegisterRequest;
import com.DatShop.exception.DuplicateAccountException;
import com.DatShop.mapper.UserMapper;
import com.DatShop.model.Role;
import com.DatShop.model.RoleName;
import com.DatShop.model.User;
import com.DatShop.model.UserStatus;
import com.DatShop.repository.RoleRepository;
import com.DatShop.repository.UserRepository;
import com.DatShop.security.JwtService;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String INVALID_CREDENTIALS = "Invalid username or password";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public CurrentUserResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateAccountException("Username already exists");
        }
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateAccountException("Email already exists");
        }

        Role customerRole = roleRepository.findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("CUSTOMER role is not configured"));

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setPhoneNumber(normalizePhone(request.phoneNumber()));
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(new HashSet<>(Set.of(customerRole)));

        return UserMapper.toCurrentUser(userRepository.save(user));
    }

    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (DisabledException ex) {
            log.debug("Login rejected because the account is not active");
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        } catch (AuthenticationException ex) {
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }

        User user = userRepository.findWithRolesByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException(INVALID_CREDENTIALS));
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.debug("Login rejected because the account is not active");
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }

        return new AuthResponse(
                jwtService.generateToken(user),
                "Bearer",
                jwtService.getExpiresInSeconds(),
                UserMapper.toAuthUser(user));
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(String username) {
        User user = userRepository.findWithRolesByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new DisabledException("Account is not active");
        }
        return UserMapper.toCurrentUser(user);
    }

    private static String normalizePhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return null;
        }
        return phoneNumber.trim();
    }
}
