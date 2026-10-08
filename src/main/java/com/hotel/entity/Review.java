package com.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Danh gia cua khach sau khi tra phong - moi don chi duoc danh gia 1 lan
@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    // So sao tu 1 den 5
    @Column(nullable = false)
    private Integer rating;

    @Column(length = 1000)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // Ten hien thi cong khai: chi lay 2 chu cuoi cua ho ten (VD "Nguyen Quoc Hoan" -> "Quoc Hoan")
    public String getDisplayName() {
        String name = customer != null && customer.getFullName() != null ? customer.getFullName().trim() : "";
        if (name.isEmpty()) {
            return "Khách hàng";
        }
        String[] parts = name.split("\\s+");
        return parts.length <= 2 ? name : parts[parts.length - 2] + " " + parts[parts.length - 1];
    }
}
