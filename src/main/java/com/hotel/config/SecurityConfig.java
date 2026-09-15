package com.hotel.config;

import com.hotel.security.AdminPortalSuccessHandler;
import com.hotel.security.CustomUserDetailsService;
import com.hotel.security.CustomerPortalSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

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

    // Cong danh cho STAFF va ADMIN - trang dang nhap tai /admin
    // Phai dat @Order(1) de duoc xet truoc chain con lai (pham vi hep hon: /admin/**, /staff/**)
    @Bean
    @Order(1)
    public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/admin", "/admin/**", "/staff/**")
                .authorizeHttpRequests(auth -> auth
                        // /admin la diem vao duy nhat hien trang dang nhap quan tri.
                        // /admin/login chi la endpoint POST do Spring Security xu ly.
                        .requestMatchers("/admin", "/admin/login").permitAll()
                        // Khu vuc quan ly nhan vien (chua xay dung) - chi danh rieng cho ADMIN
                        .requestMatchers("/admin/staff-accounts/**").hasRole("ADMIN")
                        // STAFF khong duoc TAO MOI loai phong, phong, combo, ma giam gia - chi ADMIN moi tao duoc
                        // (STAFF van xem/sua/xoa/doi trang thai binh thuong vi khong khop pattern "/new" nay)
                        .requestMatchers("/admin/room-types/new", "/admin/rooms/new",
                                "/admin/combos/new", "/admin/discount-codes/new").hasRole("ADMIN")
                        // STAFF co quyen nhu ADMIN o cac hanh dong con lai (xem, sua, xoa, doi trang thai, doanh thu...)
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers("/staff/**").hasAnyRole("STAFF", "ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/admin")
                        .loginProcessingUrl("/admin/login")
                        .successHandler(new AdminPortalSuccessHandler())
                        .failureUrl("/admin?error=true")
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
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/home", "/rooms", "/rooms/**", "/login", "/register",
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
                        .failureUrl("/login?error=true")
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
