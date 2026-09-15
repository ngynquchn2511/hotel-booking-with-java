package com.hotel.entity;

public enum PaymentMethod {
    CASH,
    BANK_TRANSFER,
    ONLINE;

    public String getVietnameseLabel() {
        return switch (this) {
            case CASH -> "Tiền mặt";
            case BANK_TRANSFER -> "Chuyển khoản ngân hàng";
            case ONLINE -> "Thanh toán online";
        };
    }
}
