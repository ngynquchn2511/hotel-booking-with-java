package com.hotel.controller;

import com.hotel.entity.BookingStatus;
import com.hotel.entity.RoomStatus;
import com.hotel.service.BookingService;
import com.hotel.service.RoomService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class AdminController {

    // So ngay mac dinh khi admin chua chon khoang ngay loc thong ke
    private static final int DEFAULT_RANGE_DAYS = 29;

    private final RoomService roomService;
    private final BookingService bookingService;

    public AdminController(RoomService roomService, BookingService bookingService) {
        this.roomService = roomService;
        this.bookingService = bookingService;
    }

    @GetMapping("/admin/dashboard")
    public String dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Model model) {

        LocalDate today = LocalDate.now();
        if (toDate == null) {
            toDate = today;
        }
        if (fromDate == null) {
            fromDate = today.minusDays(DEFAULT_RANGE_DAYS);
        }
        if (fromDate.isAfter(toDate)) {
            LocalDate swap = fromDate;
            fromDate = toDate;
            toDate = swap;
        }
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);

        model.addAttribute("totalRooms", roomService.findAll().size());
        model.addAttribute("availableRooms", roomService.countByStatus(RoomStatus.AVAILABLE));
        model.addAttribute("occupiedRooms", roomService.countByStatus(RoomStatus.OCCUPIED));
        model.addAttribute("notReadyRooms", roomService.countByStatus(RoomStatus.NOT_READY));
        model.addAttribute("maintenanceRooms", roomService.countByStatus(RoomStatus.MAINTENANCE));

        long pendingBookings = bookingService.findAllBookings().stream()
                .filter(b -> b.getStatus() == BookingStatus.PENDING)
                .count();
        model.addAttribute("pendingBookings", pendingBookings);

        // Bieu do tron: ty le don theo trang thai, trong khoang ngay da chon
        Map<BookingStatus, Long> statusCounts = bookingService.getBookingStatusCounts(fromDate, toDate);
        List<String> bookingStatusLabels = new ArrayList<>();
        List<Long> bookingStatusData = new ArrayList<>();
        statusCounts.forEach((status, count) -> {
            bookingStatusLabels.add(status.getVietnameseLabel());
            bookingStatusData.add(count);
        });
        model.addAttribute("bookingStatusLabels", bookingStatusLabels);
        model.addAttribute("bookingStatusData", bookingStatusData);

        // Bieu do cot: doanh thu theo loai phong, trong khoang ngay da chon
        Map<String, BigDecimal> revenueByRoomType = bookingService.getRevenueByRoomType(fromDate, toDate);
        model.addAttribute("roomTypeLabels", new ArrayList<>(revenueByRoomType.keySet()));
        model.addAttribute("roomTypeRevenueData", new ArrayList<>(revenueByRoomType.values()));

        // Bieu do duong: doanh thu theo ngay/thang trong khoang ngay da chon
        Map<String, BigDecimal> revenueTrend = bookingService.getRevenueTrend(fromDate, toDate);
        model.addAttribute("trendLabels", new ArrayList<>(revenueTrend.keySet()));
        model.addAttribute("trendData", new ArrayList<>(revenueTrend.values()));

        return "admin/dashboard";
    }
}
