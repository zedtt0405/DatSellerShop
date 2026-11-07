package com.DatShop.service;

import com.DatShop.dto.account.ChangePasswordRequest;
import com.DatShop.dto.account.ProfileResponse;
import com.DatShop.dto.account.ProfileUpdateRequest;
import com.DatShop.exception.BusinessRuleException;
import com.DatShop.mapper.UserMapper;
import com.DatShop.model.User;
import com.DatShop.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProfileService {

    private static final String WRONG_CURRENT_PASSWORD = "Current password is incorrect";

    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public ProfileService(
            CurrentUserService currentUserService,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public ProfileResponse getProfile(Authentication authentication) {
        return UserMapper.toProfile(currentUserService.requireUserWithRoles(authentication));
    }

    @Transactional
    public ProfileResponse updateProfile(Authentication authentication, ProfileUpdateRequest request) {
        User user = currentUserService.requireUserWithRoles(authentication);
        user.setFullName(Text.trim(request.fullName()));
        user.setPhoneNumber(Text.emptyToNull(request.phoneNumber()));
        userRepository.flush();
        return UserMapper.toProfile(user);
    }

    @Transactional
    public void changePassword(Authentication authentication, ChangePasswordRequest request) {
        User user = currentUserService.requireUser(authentication);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException(WRONG_CURRENT_PASSWORD);
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BusinessRuleException("New password and confirmation do not match");
        }
        if (request.newPassword().equals(request.currentPassword())
                || passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("New password must be different from the current password");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }
}
