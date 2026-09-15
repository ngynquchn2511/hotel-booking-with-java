package com.hotel.exception;

// Dung cho cac loi vi pham business rule (VD: BR-01 phong da duoc dat, BR-02 ngay khong hop le...)
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
