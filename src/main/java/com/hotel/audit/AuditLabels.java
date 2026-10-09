package com.hotel.audit;

import java.util.Map;

// Ten tieng Viet cua doi tuong / truong du lieu de hien thi trong nhat ky thao tac
public final class AuditLabels {

    private static final Map<String, String> ENTITY_LABELS = Map.of(
            "Booking", "Đơn đặt phòng",
            "Room", "Phòng",
            "RoomType", "Loại phòng",
            "RoomImage", "Ảnh phòng",
            "Combo", "Combo dịch vụ",
            "DiscountCode", "Mã giảm giá",
            "Payment", "Thanh toán",
            "Review", "Đánh giá",
            "BookingCharge", "Phụ phí",
            "User", "Tài khoản"
    );

    private static final Map<String, String> FIELD_LABELS = Map.ofEntries(
            Map.entry("fullName", "Họ tên"),
            Map.entry("email", "Email"),
            Map.entry("password", "Mật khẩu"),
            Map.entry("phoneNumber", "Số điện thoại"),
            Map.entry("role", "Vai trò"),
            Map.entry("customerType", "Loại khách hàng"),
            Map.entry("locked", "Khóa tài khoản"),
            Map.entry("resetToken", "Mã đặt lại mật khẩu"),
            Map.entry("resetTokenExpiry", "Hạn đặt lại mật khẩu"),
            Map.entry("customer", "Khách hàng"),
            Map.entry("room", "Phòng"),
            Map.entry("roomNumber", "Số phòng"),
            Map.entry("roomType", "Loại phòng"),
            Map.entry("checkInDate", "Ngày nhận phòng"),
            Map.entry("checkOutDate", "Ngày trả phòng"),
            Map.entry("checkInTime", "Giờ nhận phòng"),
            Map.entry("checkOutTime", "Giờ trả phòng"),
            Map.entry("checkInTimeChanged", "Đã đổi giờ"),
            Map.entry("numberOfGuests", "Số khách"),
            Map.entry("guestName", "Tên người nhận phòng"),
            Map.entry("guestPhone", "SĐT người nhận phòng"),
            Map.entry("guestEmail", "Email người nhận phòng"),
            Map.entry("totalAmount", "Tổng tiền"),
            Map.entry("status", "Trạng thái"),
            Map.entry("combo", "Combo"),
            Map.entry("discountCode", "Mã giảm giá"),
            Map.entry("discountAmount", "Tiền giảm"),
            Map.entry("name", "Tên"),
            Map.entry("code", "Mã"),
            Map.entry("description", "Mô tả"),
            Map.entry("price", "Giá"),
            Map.entry("basePrice", "Giá cơ bản"),
            Map.entry("maxGuests", "Số khách tối đa"),
            Map.entry("area", "Diện tích"),
            Map.entry("amenities", "Tiện nghi"),
            Map.entry("imageUrl", "Ảnh"),
            Map.entry("active", "Đang áp dụng"),
            Map.entry("discountType", "Loại giảm"),
            Map.entry("discountValue", "Giá trị giảm"),
            Map.entry("applicableCustomerType", "Áp dụng cho"),
            Map.entry("booking", "Đơn đặt phòng"),
            Map.entry("amount", "Số tiền"),
            Map.entry("paymentMethod", "Phương thức"),
            Map.entry("paymentDate", "Ngày thanh toán"),
            Map.entry("rating", "Số sao"),
            Map.entry("comment", "Nhận xét"),
            Map.entry("hidden", "Ẩn"),
            Map.entry("type", "Loại"),
            Map.entry("createdAt", "Ngày tạo")
    );

    private AuditLabels() {
    }

    public static Map<String, String> entityLabels() {
        return ENTITY_LABELS;
    }

    public static String entityLabel(String entityType) {
        return ENTITY_LABELS.getOrDefault(entityType, entityType);
    }

    public static String fieldLabel(String field) {
        return FIELD_LABELS.getOrDefault(field, field);
    }
}
