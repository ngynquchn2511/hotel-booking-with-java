package com.hotel.repository;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<Booking> findByStatusOrderByCreatedAtDesc(BookingStatus status);

    List<Booking> findAllByOrderByCreatedAtDesc();

    long countByNewBookingTrue();

    List<Booking> findByRoomIdAndStatusIn(Long roomId, List<BookingStatus> statuses);

    // Moi khach chi duoc dung 1 ma giam gia 1 lan - don da huy (CANCELLED) thi khong tinh, khach dung lai duoc
    boolean existsByCustomerIdAndDiscountCodeIdAndStatusNot(Long customerId, Long discountCodeId, BookingStatus status);

    // BR-01: kiem tra phong da co booking trung khoang ngay chua (dung khi tao booking moi)
    // Cong thuc trung lich: existing.checkIn < newCheckOut AND existing.checkOut > newCheckIn
    @Query("""
            SELECT COUNT(b) > 0 FROM Booking b
            WHERE b.room.id = :roomId
            AND b.status IN (
                com.hotel.entity.BookingStatus.PENDING,
                com.hotel.entity.BookingStatus.CONFIRMED,
                com.hotel.entity.BookingStatus.CHECKED_IN
            )
            AND b.checkInDate < :checkOutDate
            AND b.checkOutDate > :checkInDate
            """)
    boolean existsOverlappingBooking(
            @Param("roomId") Long roomId,
            @Param("checkInDate") LocalDate checkInDate,
            @Param("checkOutDate") LocalDate checkOutDate
    );
}
