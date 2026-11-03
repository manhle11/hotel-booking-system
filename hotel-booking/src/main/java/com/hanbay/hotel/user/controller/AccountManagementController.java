package com.hanbay.hotel.user.controller;

import com.hanbay.hotel.authentication.domain.enums.UserStatus;
import com.hanbay.hotel.common.response.ApiResponse;
import com.hanbay.hotel.common.response.PageResponse;
import com.hanbay.hotel.user.dto.AccountRoleResponse;
import com.hanbay.hotel.user.dto.CreateAccountRequest;
import com.hanbay.hotel.user.dto.UpdateAccountRequest;
import com.hanbay.hotel.user.dto.UpdateAccountRolesRequest;
import com.hanbay.hotel.user.dto.UpdateAccountStatusRequest;
import com.hanbay.hotel.user.dto.UserResponse;
import com.hanbay.hotel.user.service.AccountManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${api.prefix:/api/v1}/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Quản lý tài khoản", description = "Chỉ ADMIN được quản lý tài khoản, trạng thái và vai trò")
@RequiredArgsConstructor
public class AccountManagementController {
    private final AccountManagementService service;

    @GetMapping("/accounts")
    @Operation(summary = "Tìm kiếm tài khoản theo họ tên, email, số điện thoại, trạng thái và vai trò")
    public ApiResponse<PageResponse<UserResponse>> search(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String role) {
        return ApiResponse.success(service.search(page, size, search, status, role));
    }

    @GetMapping("/roles")
    @Operation(summary = "Danh sách vai trò có thể gán; tài khoản khách có danh sách vai trò rỗng")
    public ApiResponse<List<AccountRoleResponse>> roles() {
        return ApiResponse.success(service.roles());
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        return ApiResponse.success("Tạo tài khoản thành công", service.create(request));
    }

    @PutMapping("/accounts/{id}")
    public ApiResponse<UserResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateAccountRequest request) {
        return ApiResponse.success("Cập nhật tài khoản thành công", service.update(id, request));
    }

    @PatchMapping("/accounts/{id}/status")
    public ApiResponse<UserResponse> status(
            @PathVariable UUID id, @Valid @RequestBody UpdateAccountStatusRequest request) {
        return ApiResponse.success("Cập nhật trạng thái thành công", service.updateStatus(id, request.status()));
    }

    @PutMapping("/accounts/{id}/roles")
    public ApiResponse<UserResponse> roles(
            @PathVariable UUID id, @Valid @RequestBody UpdateAccountRolesRequest request) {
        return ApiResponse.success("Cập nhật vai trò thành công", service.updateRoles(id, request.roles()));
    }
}
