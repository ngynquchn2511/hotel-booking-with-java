package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingStatus;
import com.hotel.entity.Review;
import com.hotel.exception.BusinessException;
import com.hotel.repository.ReviewRepository;
import org.springframework.data.domain.PageRequest;
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

    // Danh gia hien thi o trang Gioi thieu (bo qua danh gia admin da an)
    public List<Review> findLatestPublicReviews() {
        return reviewRepository.findVisibleWithComment(PageRequest.of(0, 12));
    }

    // Danh gia moi nhat cua loai phong, hien o trang chi tiet phong
    public List<Review> findLatestForRoomType(Long roomTypeId, int limit) {
        return reviewRepository.findVisibleByRoomType(roomTypeId, PageRequest.of(0, limit));
    }

    // Diem trung binh (lam tron 1 chu so) va so luot danh gia dang hien cua loai phong
    public RatingStats statsForRoomType(Long roomTypeId) {
        List<Object[]> rows = reviewRepository.statsByRoomType(roomTypeId);
        if (rows.isEmpty() || rows.get(0)[0] == null) {
            return new RatingStats(0, 0);
        }
        double avg = ((Number) rows.get(0)[0]).doubleValue();
        long count = ((Number) rows.get(0)[1]).longValue();
        return new RatingStats(Math.round(avg * 10) / 10.0, count);
    }

    public record RatingStats(double average, long count) {
        // So sao day du de ve (VD 4.3 -> 4 sao dac + 1 sao rong)
        public int fullStars() {
            return (int) Math.round(average);
        }
    }

    // ===== Quan ly cua admin =====

    // Loc danh sach cho admin: theo so sao, trang thai an/hien, tu khoa (ten khach, email, noi dung, ma don)
    public List<Review> findAllForAdmin(Integer rating, Boolean hidden, String keyword) {
        String q = keyword == null ? "" : keyword.trim().toLowerCase();
        return reviewRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(r -> rating == null || r.getRating().equals(rating))
                .filter(r -> hidden == null || r.isHidden() == hidden)
                .filter(r -> q.isEmpty()
                        || contains(r.getCustomer().getFullName(), q)
                        || contains(r.getCustomer().getEmail(), q)
                        || contains(r.getComment(), q)
                        || ("#" + r.getBooking().getId()).equals(q) || String.valueOf(r.getBooking().getId()).equals(q))
                .toList();
    }

    @Transactional
    public Review toggleHidden(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy đánh giá"));
        review.setHidden(!review.isHidden());
        return reviewRepository.save(review);
    }

    private boolean contains(String value, String q) {
        return value != null && value.toLowerCase().contains(q);
    }
}
