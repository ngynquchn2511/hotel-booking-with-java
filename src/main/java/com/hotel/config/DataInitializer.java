package com.hotel.config;

import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// Tao san tai khoan ADMIN va STAFF de test dang nhap, chi tao neu chua ton tai
@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        createIfMissing("admin@hotel.com", "Quản trị viên", "0900000001", "admin123", UserRole.ADMIN);
        createIfMissing("staff@hotel.com", "Nhân viên lễ tân", "0900000002", "staff123", UserRole.STAFF);
    }

    private void createIfMissing(String email, String fullName, String phone, String rawPassword, UserRole role) {
        if (userRepository.existsByEmail(email)) {
            return;
        }
        User user = User.builder()
                .email(email)
                .fullName(fullName)
                .phoneNumber(phone)
                .password(passwordEncoder.encode(rawPassword))
                .role(role)
                .build();
        userRepository.save(user);
    }
}
