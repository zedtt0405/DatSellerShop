package com.DatShop.service;

import com.DatShop.exception.ResourceNotFoundException;
import com.DatShop.model.User;
import com.DatShop.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User requireUser(Authentication authentication) {
        return userRepository.findByUsername(username(authentication))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    public User requireUserWithRoles(Authentication authentication) {
        return userRepository.findWithRolesByUsername(username(authentication))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    public User lockCurrentUser(Authentication authentication) {
        User user = requireUser(authentication);
        return userRepository.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private static String username(Authentication authentication) {
        return authentication.getName();
    }
}
