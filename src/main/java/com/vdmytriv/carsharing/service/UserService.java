package com.vdmytriv.carsharing.service;

import com.vdmytriv.carsharing.dto.user.UserPatchRequest;
import com.vdmytriv.carsharing.dto.user.UserRegistrationRequest;
import com.vdmytriv.carsharing.dto.user.UserResponse;
import com.vdmytriv.carsharing.dto.user.UserUpdateRequest;
import com.vdmytriv.carsharing.exception.EmailAlreadyExistsException;
import com.vdmytriv.carsharing.exception.InvalidRequestException;
import com.vdmytriv.carsharing.exception.ResourceNotFoundException;
import com.vdmytriv.carsharing.mapper.UserMapper;
import com.vdmytriv.carsharing.model.Role;
import com.vdmytriv.carsharing.model.RoleName;
import com.vdmytriv.carsharing.model.User;
import com.vdmytriv.carsharing.repository.RoleRepository;
import com.vdmytriv.carsharing.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final UserRepository userRepository;

    @Transactional
    public UserResponse register(UserRegistrationRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }
        Role customerRole = findRole(RoleName.CUSTOMER);
        User user = userMapper.toModel(request);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(customerRole);
        try {
            return userMapper.toResponse(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyExistsException(email);
        }
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        return userMapper.toResponse(findUserByEmail(email));
    }

    @Transactional
    public UserResponse updateCurrentUser(String email, UserUpdateRequest request) {
        User user = findUserByEmail(email);
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse patchCurrentUser(String email, UserPatchRequest request) {
        if (request.firstName() == null && request.lastName() == null) {
            throw new InvalidRequestException("At least one profile field must be provided");
        }
        User user = findUserByEmail(email);
        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateRole(Long userId, RoleName roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        user.setRole(findRole(roleName));
        return userMapper.toResponse(user);
    }

    private User findUserByEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        return userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", normalizedEmail));
    }

    private Role findRole(RoleName roleName) {
        return roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException(
                        "Required role is not configured: " + roleName
                ));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
