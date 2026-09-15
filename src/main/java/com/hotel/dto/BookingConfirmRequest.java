package com.hotel.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
public class BookingConfirmRequest {

    @NotBlank(message = "Vui lòng nhập họ tên người nhận phòng")
    private String guestName;

    @NotBlank(message = "Vui lòng nhập email")
    @Email(message = "Email không hợp lệ")
    private String guestEmail;

    @NotBlank(message = "Vui lòng nhập số điện thoại")
    @Pattern(regexp = "^0[0-9]{9}$", message = "Số điện thoại phải có 10 số và bắt đầu bằng 0")
    private String guestPhone;

    @NotNull(message = "Vui lòng chọn giờ nhận phòng")
    private LocalTime checkInTime;

    @NotNull(message = "Vui lòng chọn giờ trả phòng")
    private LocalTime checkOutTime;

    // Khong bat buoc - de trong neu khach khong dung ma giam gia
    private String discountCode;
}
