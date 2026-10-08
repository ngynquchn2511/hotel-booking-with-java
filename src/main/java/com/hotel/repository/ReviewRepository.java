package com.hotel.repository;

import com.hotel.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByBookingId(Long bookingId);

    boolean existsByBookingId(Long bookingId);

    List<Review> findByBookingIdIn(Collection<Long> bookingIds);

    // Danh gia moi nhat co noi dung, dung cho trang Gioi thieu
    List<Review> findTop12ByCommentIsNotNullOrderByCreatedAtDesc();
}
