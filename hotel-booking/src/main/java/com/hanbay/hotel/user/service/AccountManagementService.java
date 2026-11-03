package com.hanbay.hotel.user.service;

import com.hanbay.hotel.authentication.domain.entity.Role;
import com.hanbay.hotel.authentication.domain.enums.RoleCode;
import com.hanbay.hotel.authentication.domain.enums.UserStatus;
import com.hanbay.hotel.authentication.exception.EmailAlreadyExistsException;
import com.hanbay.hotel.authentication.exception.PhoneAlreadyExistsException;
import com.hanbay.hotel.authentication.repository.RefreshTokenRepository;
import com.hanbay.hotel.authentication.repository.RoleRepository;
import com.hanbay.hotel.authentication.service.AccessTokenRevocationService;
import com.hanbay.hotel.common.exception.RequestValidationException;
import com.hanbay.hotel.common.response.PageResponse;
import com.hanbay.hotel.common.security.CurrentUserProvider;
import com.hanbay.hotel.user.domain.User;
import com.hanbay.hotel.user.dto.AccountRoleResponse;
import com.hanbay.hotel.user.dto.CreateAccountRequest;
import com.hanbay.hotel.user.dto.UpdateAccountRequest;
import com.hanbay.hotel.user.dto.UserResponse;
import com.hanbay.hotel.user.exception.AccountManagementException;
import com.hanbay.hotel.user.exception.UserNotFoundException;
import com.hanbay.hotel.user.mapper.UserMapper;
import com.hanbay.hotel.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountManagementService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserProvider currentUserProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenRevocationService accessTokenRevocationService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(int page, int size, String search, UserStatus status, String role) {
        if (page < 0 || size < 1 || size > 100) {
            throw new RequestValidationException("page", "Trang phải từ 0 và kích thước trang từ 1 đến 100");
        }
        boolean guest = "GUEST".equals(role);
        RoleCode roleCode;
        try {
            roleCode = role == null || guest ? null : RoleCode.valueOf(role);
        } catch (IllegalArgumentException exception) {
            throw new RequestValidationException("role", "Vai trò không hợp lệ");
        }
        String keyword = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        if (keyword.length() > 150) throw new RequestValidationException("search", "Từ khóa tối đa 150 ký tự");
        String pattern = "%" + keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        Specification<User> criteria = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!keyword.isEmpty()) {
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("fullName")), pattern, '\\'),
                        builder.like(builder.lower(root.get("email")), pattern, '\\'),
                        builder.like(root.get("phone"), pattern, '\\')));
            }
            if (status != null) predicates.add(builder.equal(root.get("status"), status));
            if (guest) predicates.add(builder.isEmpty(root.get("roles")));
            if (roleCode != null)
                predicates.add(builder.equal(root.join("roles").get("code"), roleCode));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(userRepository
                .findAll(
                        criteria,
                        PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))))
                .map(userMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public List<AccountRoleResponse> roles() {
        return roleRepository.findAll().stream()
                .sorted(Comparator.comparing(Role::getCode))
                .map(role -> new AccountRoleResponse(role.getCode(), role.getName(), role.getDescription()))
                .toList();
    }

    @Transactional
    public UserResponse create(CreateAccountRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String fullName = fullName(request.fullName());
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new RequestValidationException("password", "Mật khẩu không được vượt quá 72 byte");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) throw new EmailAlreadyExistsException(email);
        if (request.phone() != null && userRepository.existsByPhone(request.phone())) {
            throw new PhoneAlreadyExistsException(request.phone());
        }
        User user = User.builder()
                .email(email)
                .fullName(fullName)
                .phone(request.phone())
                .passwordHash(passwordEncoder.encode(request.password()))
                .roles(resolveRoles(request.roles()))
                .build();
        return userMapper.toResponse(userRepository.saveAndFlush(user));
    }

    @Transactional
    public UserResponse update(UUID id, UpdateAccountRequest request) {
        User user = accountForUpdate(id);
        if (request.phone() != null && userRepository.existsByPhoneAndIdNot(request.phone(), id)) {
            throw new PhoneAlreadyExistsException(request.phone());
        }
        user.setFullName(fullName(request.fullName()));
        user.setPhone(request.phone());
        return userMapper.toResponse(userRepository.saveAndFlush(user));
    }

    @Transactional
    public UserResponse updateStatus(UUID id, UserStatus status) {
        lockAdminRole();
        User user = accountForUpdate(id);
        if (user.getStatus() == status) return userMapper.toResponse(user);
        preventSelfChange(id);
        if (status != UserStatus.ACTIVE) protectLastAdmin(user);
        user.setStatus(status);
        UserResponse response = userMapper.toResponse(userRepository.saveAndFlush(user));
        revokeSessions(id);
        return response;
    }

    @Transactional
    public UserResponse updateRoles(UUID id, Set<RoleCode> codes) {
        lockAdminRole();
        User user = accountForUpdate(id);
        Set<Role> roles = resolveRoles(codes);
        Set<RoleCode> current = new LinkedHashSet<>();
        user.getRoles().forEach(role -> current.add(role.getCode()));
        if (current.equals(codes)) return userMapper.toResponse(user);
        preventSelfChange(id);
        if (!codes.contains(RoleCode.ADMIN)) protectLastAdmin(user);
        user.setRoles(roles);
        UserResponse response = userMapper.toResponse(userRepository.saveAndFlush(user));
        revokeSessions(id);
        return response;
    }

    private Set<Role> resolveRoles(Set<RoleCode> codes) {
        if (codes.size() > 1) {
            throw new RequestValidationException("roles", "Mỗi tài khoản chỉ được có một vai trò");
        }
        Set<Role> roles = new LinkedHashSet<>();
        for (RoleCode code : codes) {
            roles.add(roleRepository
                    .findByCode(code)
                    .orElseThrow(() -> new RequestValidationException("roles", "Vai trò không tồn tại")));
        }
        return roles;
    }

    private User accountForUpdate(UUID id) {
        return userRepository.findAccountForUpdate(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    private String fullName(String value) {
        String name = value.trim();
        if (name.length() < 2 || name.length() > 100) {
            throw new RequestValidationException("fullName", "Họ tên phải có từ 2 đến 100 ký tự");
        }
        return name;
    }

    private void preventSelfChange(UUID id) {
        if (currentUserProvider.currentUserId().filter(id::equals).isPresent()) {
            throw new AccountManagementException(
                    "ACCOUNT_SELF_CHANGE", "Không thể tự chặn hoặc thay đổi vai trò của chính mình");
        }
    }

    private void lockAdminRole() {
        roleRepository
                .findByCodeForUpdate(RoleCode.ADMIN)
                .orElseThrow(() -> new IllegalStateException("Vai trò ADMIN chưa được khởi tạo"));
    }

    private void protectLastAdmin(User user) {
        if (user.getStatus() == UserStatus.ACTIVE
                && user.getRoles().stream().anyMatch(role -> role.getCode() == RoleCode.ADMIN)
                && userRepository.countByStatusAndRoles_Code(UserStatus.ACTIVE, RoleCode.ADMIN) <= 1) {
            throw new AccountManagementException(
                    "ACCOUNT_LAST_ADMIN", "Phải giữ ít nhất một quản trị viên đang hoạt động");
        }
    }

    private void revokeSessions(UUID id) {
        refreshTokenRepository.revokeActiveTokensByUserId(id, clock.instant());
        accessTokenRevocationService.revokeAll(id);
    }
}
