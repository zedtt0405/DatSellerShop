package com.DatShop.mapper;

import com.DatShop.dto.account.ProfileResponse;
import com.DatShop.dto.auth.AuthUserResponse;
import com.DatShop.dto.auth.CurrentUserResponse;
import com.DatShop.model.User;
import java.util.List;

public final class UserMapper {

    private UserMapper() {
    }

    public static CurrentUserResponse toCurrentUser(User user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getPhoneNumber(),
                user.getStatus().name(),
                roleNames(user));
    }

    public static ProfileResponse toProfile(User user) {
        return new ProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getPhoneNumber(),
                user.getStatus().name(),
                roleNames(user),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    public static AuthUserResponse toAuthUser(User user) {
        return new AuthUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                roleNames(user));
    }

    private static List<String> roleNames(User user) {
        return user.getRoles().stream()
                .map(role -> role.getName().name())
                .sorted()
                .toList();
    }
}
