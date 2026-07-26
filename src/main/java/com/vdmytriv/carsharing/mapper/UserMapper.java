package com.vdmytriv.carsharing.mapper;

import com.vdmytriv.carsharing.dto.user.UserRegistrationRequest;
import com.vdmytriv.carsharing.dto.user.UserResponse;
import com.vdmytriv.carsharing.model.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public User toModel(UserRegistrationRequest request) {
        User user = new User();
        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        return user;
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole().getName()
        );
    }
}
