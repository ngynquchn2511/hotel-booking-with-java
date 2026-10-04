package com.hotel.service;

import com.hotel.dto.RegisterRequest;
import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.exception.BusinessException;
import com.hotel.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

@Service
public class UserService {

    // Lien ket dat lai mat khau chi co hieu luc trong 30 phut
    public static final int RESET_TOKEN_VALID_MINUTES = 30;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User registerCustomer(RegisterRequest request) {
        // Business rule: email không được trùng
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email này đã được đăng ký, vui lòng dùng email khác");
        }

        // Business rule: mật khẩu xác nhận phải giống mật khẩu
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("Mật khẩu xác nhận không khớp");
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(UserRole.CUSTOMER)
                .build();

        return userRepository.save(user);
    }

    // Tao token dat lai mat khau cho tai khoan khach hang co email nay.
    // Tra ve Optional.empty() neu email chua dang ky tai khoan khach hang.
    @Transactional
    public Optional<User> createPasswordResetToken(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmail(email.trim())
                .filter(user -> user.getRole() == UserRole.CUSTOMER)
                .map(user -> {
                    byte[] bytes = new byte[32];
                    RANDOM.nextBytes(bytes);
                    user.setResetToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
                    user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(RESET_TOKEN_VALID_MINUTES));
                    return userRepository.save(user);
                });
    }

    public boolean isResetTokenValid(String token) {
        return findValidResetUser(token).isPresent();
    }

    @Transactional
    public void resetPassword(String token, String newPassword, String confirmPassword) {
        User user = findValidResetUser(token).orElseThrow(() ->
                new BusinessException("Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn"));

        if (!newPassword.equals(confirmPassword)) {
            throw new BusinessException("Mật khẩu xác nhận không khớp");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        // Token chi dung duoc 1 lan
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
    }

    private Optional<User> findValidResetUser(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByResetToken(token)
                .filter(user -> user.getResetTokenExpiry() != null
                        && user.getResetTokenExpiry().isAfter(LocalDateTime.now()));
    }
}
