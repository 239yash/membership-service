package com.work.membership_service.web.controller;

import com.work.membership_service.model.entity.record.ApiResponse;
import com.work.membership_service.model.entity.UserAccount;
import com.work.membership_service.service.user.UserService;
import com.work.membership_service.web.dto.request.CreateUserRequest;
import com.work.membership_service.web.dto.response.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody CreateUserRequest request) {
        log.info("[user_flow] create user email: {}", request.email());
        UserAccount created = userService.create(request.name(), request.email(), request.cohorts());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(toResponse(created)));
    }

    @GetMapping("/{userId}")
    public ApiResponse<UserResponse> get(@PathVariable Long userId) {
        log.debug("[user_flow] get user id: {}", userId);
        return ApiResponse.ok(toResponse(userService.getById(userId)));
    }

    private UserResponse toResponse(UserAccount user) {
        List<String> cohorts = user.getCohorts() == null
                ? List.of()
                : Arrays.asList(user.getCohorts());
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), cohorts, user.getCreatedAt());
    }
}
