package com.vdmytriv.carsharing.controller;

import com.vdmytriv.carsharing.dto.user.UserPatchRequest;
import com.vdmytriv.carsharing.dto.user.UserResponse;
import com.vdmytriv.carsharing.dto.user.UserRoleUpdateRequest;
import com.vdmytriv.carsharing.dto.user.UserUpdateRequest;
import com.vdmytriv.carsharing.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Users", description = "Current user profile and role management")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Get the current user profile")
    @GetMapping("/me")
    public UserResponse getCurrentUser(Principal principal) {
        return userService.getCurrentUser(principal.getName());
    }

    @Operation(summary = "Replace the current user profile")
    @PutMapping("/me")
    public UserResponse updateCurrentUser(
            Principal principal,
            @RequestBody @Valid UserUpdateRequest request
    ) {
        return userService.updateCurrentUser(principal.getName(), request);
    }

    @Operation(summary = "Partially update the current user profile")
    @PatchMapping("/me")
    public UserResponse patchCurrentUser(
            Principal principal,
            @RequestBody @Valid UserPatchRequest request
    ) {
        return userService.patchCurrentUser(principal.getName(), request);
    }

    @Operation(summary = "Update a user's role")
    @PreAuthorize("hasRole('MANAGER')")
    @PutMapping("/{id}/role")
    public UserResponse updateRole(
            @PathVariable Long id,
            @RequestBody @Valid UserRoleUpdateRequest request
    ) {
        return userService.updateRole(id, request.role());
    }
}
