package com.hotel.repository;

import com.hotel.entity.BookingCharge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingChargeRepository extends JpaRepository<BookingCharge, Long> {

    List<BookingCharge> findByBookingIdOrderByCreatedAtAsc(Long bookingId);
}
