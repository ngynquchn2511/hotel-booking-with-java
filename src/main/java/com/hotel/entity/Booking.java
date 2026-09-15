package com.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;

    // Gio nhan/tra phong cu the ma khach chon (VD: nhan 14:00, tra 12:00) - khac voi ngay nhan/tra
    @Column(name = "check_in_time", nullable = false)
    private java.time.LocalTime checkInTime;

    @Column(name = "check_out_time", nullable = false)
    private java.time.LocalTime checkOutTime;

    @Column(name = "number_of_guests")
    private Integer numberOfGuests;

    // Thong tin lien he cua nguoi truc tiep nhan phong (co the khac tai khoan neu dat ho nguoi khac)
    @Column(name = "guest_name")
    private String guestName;

    @Column(name = "guest_phone")
    private String guestPhone;

    @Column(name = "guest_email")
    private String guestEmail;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    // Combo dich vu khach chon (co the null = phong thuong, khong combo)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "combo_id")
    private Combo combo;

    // Ma giam gia da ap dung (co the null = khong dung ma)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discount_code_id")
    private DiscountCode discountCode;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    private java.math.BigDecimal discountAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Danh dau don moi tao (khach vua dat) de hien thi cham do thong bao cho admin/staff - tat khi staff mo xem chi tiet
    @Column(name = "new_booking", nullable = false)
    @Builder.Default
    private boolean newBooking = true;

    // Danh dau khach vua tu doi gio nhan/tra phong - hien canh bao rieng cho staff, tat khi staff mo xem chi tiet
    @Column(name = "check_in_time_changed", nullable = false)
    @Builder.Default
    private boolean checkInTimeChanged = false;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = BookingStatus.PENDING;
        }
    }
}
