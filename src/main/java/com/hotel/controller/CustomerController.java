package com.hotel.controller;

import com.hotel.exception.BusinessException;
import com.hotel.repository.RoomTypeRepository;
import com.hotel.service.ComboService;
import com.hotel.service.RoomService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Controller
public class CustomerController {

    private final RoomService roomService;
    private final RoomTypeRepository roomTypeRepository;
    private final ComboService comboService;

    public CustomerController(RoomService roomService, RoomTypeRepository roomTypeRepository,
                               ComboService comboService) {
        this.roomService = roomService;
        this.roomTypeRepository = roomTypeRepository;
        this.comboService = comboService;
    }

    // Trang tim phong - hien form tim kiem, neu co du checkIn/checkOut thi hien ket qua
    @GetMapping("/customer/rooms")
    public String searchRooms(
            @RequestParam(required = false) LocalDate checkIn,
            @RequestParam(required = false) LocalDate checkOut,
            @RequestParam(required = false) Integer guests,
            @RequestParam(required = false) Long roomTypeId,
            Model model) {

        model.addAttribute("roomTypes", roomTypeRepository.findAll());
        model.addAttribute("checkIn", checkIn);
        model.addAttribute("checkOut", checkOut);
        model.addAttribute("guests", guests);
        model.addAttribute("roomTypeId", roomTypeId);
        model.addAttribute("today", LocalDate.now());

        boolean searched = checkIn != null || checkOut != null;
        model.addAttribute("searched", searched);

        if (searched) {
            try {
                List<com.hotel.entity.Room> rooms = roomService.searchAvailableRooms(checkIn, checkOut, guests, roomTypeId);
                model.addAttribute("rooms", rooms);
            } catch (BusinessException ex) {
                model.addAttribute("errorMessage", ex.getMessage());
            }
        }

        return "customer/rooms";
    }

    // Xem chi tiet phong, giu lai ngay da chon (neu co) de tinh tong tien va kiem tra con trong
    // Cho phep chon combo dich vu di kem (an nuong / lau / phong thuong)
    @GetMapping("/customer/rooms/{id}")
    public String viewRoomDetail(
            @PathVariable Long id,
            @RequestParam(required = false) LocalDate checkIn,
            @RequestParam(required = false) LocalDate checkOut,
            @RequestParam(required = false) Integer guests,
            @RequestParam(required = false) Long comboId,
            Model model) {

        var room = roomService.findById(id);
        model.addAttribute("room", room);
        model.addAttribute("unavailableDates", roomService.findUnavailableDates(id).stream()
                .map(LocalDate::toString)
                .toList());
        model.addAttribute("checkIn", checkIn);
        model.addAttribute("checkOut", checkOut);
        model.addAttribute("guests", guests);
        model.addAttribute("combos", comboService.findActive());
        model.addAttribute("selectedComboId", comboId);

        if (checkIn != null && checkOut != null) {
            try {
                if (checkIn.isBefore(LocalDate.now())) {
                    throw new BusinessException("Ngày nhận phòng không được ở trong quá khứ");
                }
                if (!checkOut.isAfter(checkIn)) {
                    throw new BusinessException("Ngày trả phòng phải lớn hơn ngày nhận phòng");
                }
                long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
                var roomAmount = room.getPrice().multiply(java.math.BigDecimal.valueOf(nights));

                // Neu khach da chon combo thi cong them gia combo (tinh 1 lan, khong nhan theo dem)
                var comboAmount = java.math.BigDecimal.ZERO;
                if (comboId != null) {
                    var combo = comboService.findById(comboId);
                    comboAmount = combo.getPrice();
                    model.addAttribute("selectedCombo", combo);
                }

                var totalAmount = roomAmount.add(comboAmount);
                boolean available = roomService.isAvailableForDates(id, checkIn, checkOut);

                model.addAttribute("nights", nights);
                model.addAttribute("roomAmount", roomAmount);
                model.addAttribute("comboAmount", comboAmount);
                model.addAttribute("totalAmount", totalAmount);
                model.addAttribute("available", available);
            } catch (BusinessException ex) {
                model.addAttribute("errorMessage", ex.getMessage());
            }
        }

        return "customer/room-detail";
    }
}
