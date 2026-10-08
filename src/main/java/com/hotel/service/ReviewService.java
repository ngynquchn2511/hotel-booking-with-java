package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingStatus;
import com.hotel.entity.Review;
import com.hotel.exception.BusinessException;
import com.hotel.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    private static final int MAX_COMMENT_LENGTH = 1000;

    private final ReviewRepository reviewRepository;
    private final BookingService bookingService;

    public ReviewService(ReviewRepository reviewRepository, BookingService bookingService) {
        this.reviewRepository = reviewRepository;
        this.bookingService = bookingService;
    }

    public Optional<Review> findByBookingId(Long bookingId) {
        return reviewRepository.findByBookingId(bookingId);
    }

    public Map<Long, Review> findByBookings(Collection<Booking> bookings) {
        if (bookings.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = bookings.stream().map(Booking::getId).toList();
        return reviewRepository.findByBookingIdIn(ids).stream()
                .collect(Collectors.toMap(r -> r.getBooking().getId(), Function.identity()));
    }

    // Chi chu don, don da tra phong (CHECKED_OUT) va chua danh gia moi duoc viet danh gia
    public boolean canReview(Booking booking, Long customerId) {
        return customerId != null
                && booking.getCustomer().getId().equals(customerId)
                && booking.getStatus() == BookingStatus.CHECKED_OUT
                && !reviewRepository.existsByBookingId(booking.getId());
    }

    @Transactional
    public Review createReview(Long bookingId, Long customerId, Integer rating, String comment) {
        Booking booking = bookingService.findByIdForCustomer(bookingId, customerId);
        if (booking.getStatus() != BookingStatus.CHECKED_OUT) {
            throw new BusinessException("Chỉ có thể đánh giá sau khi đã trả phòng");
        }
        if (reviewRepository.existsByBookingId(bookingId)) {
            throw new BusinessException("Bạn đã đánh giá đơn này rồi");
        }
        if (rating == null || rating < 1 || rating > 5) {
            throw new BusinessException("Vui lòng chọn số sao từ 1 đến 5");
        }
        String text = comment == null ? null : comment.trim();
        if (text != null && text.length() > MAX_COMMENT_LENGTH) {
            throw new BusinessException("Nhận xét tối đa " + MAX_COMMENT_LENGTH + " ký tự");
        }
        Review review = Review.builder()
                .booking(booking)
                .customer(booking.getCustomer())
                .rating(rating)
                .comment(text == null || text.isEmpty() ? null : text)
                .build();
        return reviewRepository.save(review);
    }

    // Danh gia hien thi o trang Gioi thieu
    public List<Review> findLatestPublicReviews() {
        return reviewRepository.findTop12ByCommentIsNotNullOrderByCreatedAtDesc();
    }
}
