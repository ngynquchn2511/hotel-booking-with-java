package com.hotel.integration;

import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void ensureFixedAccounts() {
        // DataInitializer da tao san admin@hotel.com / staff@hotel.com khi context khoi dong
        if (!userRepository.existsByEmail("customer.it@mail.com")) {
            userRepository.save(User.builder()
                    .fullName("Khach Test").email("customer.it@mail.com")
                    .phoneNumber("0909090909")
                    .password(passwordEncoder.encode("Customer123"))
                    .role(UserRole.CUSTOMER)
                    .build());
        }
    }

    @Test
    void register_newAccount_redirectsToLoginWithRegisteredFlag() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("fullName", "Nguoi Dung Moi")
                        .param("email", "newuser@mail.com")
                        .param("phoneNumber", "0912345678")
                        .param("password", "Abc12345")
                        .param("confirmPassword", "Abc12345"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered=true"));

        assertTrue(userRepository.existsByEmail("newuser@mail.com"));
    }

    @Test
    void register_duplicateEmail_rendersFormWithErrorAndDoesNotCreateDuplicate() throws Exception {
        long before = userRepository.count();

        mockMvc.perform(post("/register").with(csrf())
                        .param("fullName", "Khac Ten")
                        .param("email", "customer.it@mail.com")
                        .param("phoneNumber", "0911111111")
                        .param("password", "Abc12345")
                        .param("confirmPassword", "Abc12345"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().attributeExists("errorMessage"));

        assertEquals(before, userRepository.count());
    }

    @Test
    void register_passwordMismatch_rejectedByValidation() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("fullName", "Ai Do")
                        .param("email", "mismatch@mail.com")
                        .param("phoneNumber", "0911112222")
                        .param("password", "Abc12345")
                        .param("confirmPassword", "KhacHan"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"));

        assertFalse(userRepository.existsByEmail("mismatch@mail.com"));
    }

    @Test
    void login_customerCorrectCredentials_redirectsToRoomsList() throws Exception {
        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "customer.it@mail.com")
                        .param("password", "Customer123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/rooms"));
    }

    @Test
    void login_wrongPassword_redirectsToLoginError() throws Exception {
        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "customer.it@mail.com")
                        .param("password", "SaiMatKhau"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=true"));
    }

    @Test
    void login_adminAccountOnCustomerPortal_blockedAsWrongPortal() throws Exception {
        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "admin@hotel.com")
                        .param("password", "admin123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=wrong_portal"));
    }

    @Test
    void adminLogin_staffAccount_redirectsToDashboard() throws Exception {
        mockMvc.perform(post("/admin/login").with(csrf())
                        .param("username", "staff@hotel.com")
                        .param("password", "staff123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"));
    }

    @Test
    void adminLogin_customerAccount_blockedAsWrongPortal() throws Exception {
        mockMvc.perform(post("/admin/login").with(csrf())
                        .param("username", "customer.it@mail.com")
                        .param("password", "Customer123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin?error=wrong_portal"));
    }

    @Test
    void anonymousAccessToBookingHistory_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/customer/bookings"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void anonymousAccessToAdminDashboard_redirectsToAdminLogin() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/admin"));
    }
}
