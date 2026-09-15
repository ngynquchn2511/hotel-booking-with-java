package com.hotel.dto;

import com.hotel.entity.CustomerType;
import com.hotel.entity.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class DiscountCodeRequest {

    @NotBlank(message = "Mã giảm giá không được để trống")
    private String code;

    private String description;

    @NotNull(message = "Vui lòng chọn loại giảm giá")
    private DiscountType discountType;

    @NotNull(message = "Giá trị giảm không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá trị giảm phải lớn hơn 0")
    private BigDecimal discountValue;

    // De trong = ap dung cho tat ca loai khach
    private CustomerType applicableCustomerType;
}
