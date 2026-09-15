package com.hotel.config;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingStatus;
import com.hotel.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// Neu don dat phong con o trang thai PENDING (chua duoc nhan vien/admin xac nhan)
// ma da can qua it hon 6 tieng so voi gio nhan phong, tu dong huy don (dat phong that bai)
@Component
public class BookingExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(BookingExpirationScheduler.class);
    private static final int CONFIRM_DEADLINE_HOURS = 6;

    private final BookingRepository bookingRepository;

    public BookingExpirationScheduler(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    // Chay moi 5 phut de kiem tra
    @Scheduled(fixedRate = 5 * 60 * 1000)
    @Transactional
    public void expireUnconfirmedBookings() {
        List<Booking> pendingBookings = bookingRepository.findByStatusOrderByCreatedAtDesc(BookingStatus.PENDING);
        LocalDateTime now = LocalDateTime.now();
        int expiredCount = 0;

        for (Booking booking : pendingBookings) {
            LocalDateTime checkInDateTime = LocalDateTime.of(booking.getCheckInDate(), booking.getCheckInTime());
            LocalDateTime confirmDeadline = checkInDateTime.minusHours(CONFIRM_DEADLINE_HOURS);

            if (now.isAfter(confirmDeadline)) {
                booking.setStatus(BookingStatus.CANCELLED);
                bookingRepository.save(booking);
                expiredCount++;
            }
        }

        if (expiredCount > 0) {
            log.info("Da tu dong huy {} don dat phong vi khong duoc xac nhan truoc {} tieng so voi gio nhan phong",
                    expiredCount, CONFIRM_DEADLINE_HOURS);
        }
    }
}
