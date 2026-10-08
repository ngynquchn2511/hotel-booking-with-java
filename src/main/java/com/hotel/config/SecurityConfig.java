package com.hotel.config;

import com.hotel.security.AdminPortalSuccessHandler;
import com.hotel.security.CustomUserDetailsService;
import com.hotel.security.CustomerPortalSuccessHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    public SecurityConfig(CustomUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Spring Security 6 chi tao CsrfToken THUC SU (va phien HTTP di kem) khi co noi nao do GOI
    // csrfToken.getToken() lan dau (lazy, de chong BREACH). Fragment chatbot-widget doc "_csrf.token"
    // o GAN CUOI trang (sau rat nhieu CSS/JS nhung) - voi trang du lon (rooms.html, customer/rooms.html),
    // luc Thymeleaf render toi do thi Tomcat da flush/commit response roi, nen khong the tao session moi
    // nua -> IllegalStateException "Cannot create a session after the response has been committed",
    // ung dung bi cat response giua chung (thieu han phan footer). Filter nay ep "cham" vao token ngay
    // tu dau request (truoc khi co byte nao duoc ghi ra), dung theo khuyen nghi chinh thuc cua Spring
    // Security cho tinh huong nay.
    @Bean
    public OncePerRequestFilter csrfTokenEagerLoadFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                    throws ServletException, IOException {
                CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
                if (csrfToken != null) {
                    csrfToken.getToken();
                }
                filterChain.doFilter(request, response);
            }
        };
    }

    // Tai khoan bi khoa -> bao rieng "?locked=true", con lai (sai email/mat khau) -> "?error=true"
    private AuthenticationFailureHandler loginFailureHandler(String loginPage) {
        return (request, response, exception) -> response.sendRedirect(request.getContextPath() + loginPage
                + (exception instanceof LockedException ? "?locked=true" : "?error=true"));
    }

    // Cong danh cho STAFF va ADMIN - trang dang nhap tai /admin
    // Phai dat @Order(1) de duoc xet truoc chain con lai (pham vi hep hon: /admin/**, /staff/**)
    @Bean
    @Order(1)
    public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
        http
                .addFilterAfter(csrfTokenEagerLoadFilter(), CsrfFilter.class)
                .securityMatcher("/admin", "/admin/**", "/staff/**")
                .authorizeHttpRequests(auth -> auth
                        // /admin la diem vao duy nhat hien trang dang nhap quan tri.
                        // /admin/login chi la endpoint POST do Spring Security xu ly.
                        .requestMatchers("/admin", "/admin/login").permitAll()
                        // Quan ly tai khoan nhan vien va xem nhat ky thao tac - chi danh rieng cho ADMIN
                        .requestMatchers("/admin/staff-accounts", "/admin/staff-accounts/**",
                                "/admin/audit-logs", "/admin/audit-logs/**").hasRole("ADMIN")
                        // STAFF co moi quyen nhu ADMIN o cac chuc nang con lai (them/sua/xoa phong, combo, ma giam gia, doanh thu...)
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers("/staff/**").hasAnyRole("STAFF", "ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/admin")
                        .loginProcessingUrl("/admin/login")
                        .successHandler(new AdminPortalSuccessHandler())
                        .failureHandler(loginFailureHandler("/admin"))
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutSuccessUrl("/admin?logout=true")
                        .permitAll()
                )
                .userDetailsService(userDetailsService);

        return http.build();
    }

    // Cong danh cho CUSTOMER (va khach vang lai) - dang nhap tai /login
    // @Order(2) - chi xu ly cac request khong khop chain o tren
    @Bean
    @Order(2)
    public SecurityFilterChain customerFilterChain(HttpSecurity http) throws Exception {
        http
                .addFilterAfter(csrfTokenEagerLoadFilter(), CsrfFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/home", "/about", "/rooms", "/rooms/**", "/login", "/register",
                                "/forgot-password", "/reset-password",
                                "/css/**", "/js/**", "/images/**", "/uploads/**", "/page-images/**").permitAll()
                        // Chatbot tu van - khach chua dang nhap cung phai hoi duoc
                        .requestMatchers("/api/chatbot/**").permitAll()
                        // Tim phong, xem chi tiet phong, dat phong, kiem tra ma giam gia, xem lai 1 don vua dat
                        // KHONG can dang nhap - khach nhap ho ten/email/SDT ngay luc dat phong
                        .requestMatchers("/customer/rooms", "/customer/rooms/*",
                                "/customer/bookings/new", "/customer/bookings/check-discount",
                                "/customer/bookings/*").permitAll()
                        // Rieng lich su dat phong va sua/huy don van can dang nhap (de xac dinh dung chu don)
                        .requestMatchers("/customer/**").hasRole("CUSTOMER")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .successHandler(new CustomerPortalSuccessHandler())
                        .failureHandler(loginFailureHandler("/login"))
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=true")
                        .permitAll()
                )
                .userDetailsService(userDetailsService);

        return http.build();
    }
}
