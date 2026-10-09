package com.hotel.service;

import com.hotel.entity.*;
import com.hotel.exception.BusinessException;
import com.hotel.repository.BookingChargeRepository;
import com.hotel.repository.BookingGuestRepository;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.ComboRepository;
import com.hotel.repository.DiscountCodeRepository;
import com.hotel.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BookingService {

    // Chi cho phep sua gio nhan/tra hoac doi combo neu con truoc it nhat 6 tieng so voi gio nhan phong
    private static final int EDIT_CUTOFF_HOURS = 6;

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final ComboRepository comboRepository;
    private final DiscountCodeRepository discountCodeRepository;
    private final EmailService emailService;
    private final BookingChargeRepository chargeRepository;
    private final PricingService pricingService;
    private final BookingGuestRepository guestRepository;

    public BookingService(BookingRepository bookingRepository, RoomRepository roomRepository,
                           ComboRepository comboRepository, DiscountCodeRepository discountCodeRepository,
                           EmailService emailService, BookingChargeRepository chargeRepository,
                           PricingService pricingService, BookingGuestRepository guestRepository) {
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
        this.comboRepository = comboRepository;
        this.discountCodeRepository = discountCodeRepository;
        this.emailService = emailService;
        this.chargeRepository = chargeRepository;
        this.pricingService = pricingService;
        this.guestRepository = guestRepository;
    }

    public static final String DISCOUNT_ALREADY_USED_MESSAGE =
            "Bạn đã sử dụng mã giảm giá này rồi. Mỗi mã chỉ được dùng 1 lần cho mỗi khách hàng";

    // Dung cho nut "Kiem tra ma" (xem truoc) - luong tao booking van tu kiem tra lai trong createBooking
    public boolean hasUsedDiscountCode(Long customerId, String code) {
        String normalizedCode = code == null ? "" : code.trim().toUpperCase();
        return discountCodeRepository.findByCodeAndActiveTrue(normalizedCode)
                .map(dc -> bookingRepository.existsByCustomerIdAndDiscountCodeIdAndStatusNot(
                        customerId, dc.getId(), BookingStatus.CANCELLED))
                .orElse(false);
    }

    // Khach dat online: don cho xac nhan, neu homestay bat dat coc thi kem so tien + han chuyen coc
    @Transactional
    public Booking createBooking(User customer, Long roomId, LocalDate checkInDate, LocalDate checkOutDate,
                                  LocalTime checkInTime, LocalTime checkOutTime, Integer guests,
                                  Long comboId, String discountCodeStr, String guestName, String guestPhone,
                                  String guestEmail) {
        Booking booking = buildBooking(customer, roomId, checkInDate, checkOutDate, checkInTime, checkOutTime,
                guests, comboId, discountCodeStr, guestName, guestPhone, guestEmail);

        BigDecimal deposit = pricingService.depositFor(booking.getTotalAmount());
        booking.setDepositAmount(deposit);
        if (deposit.signum() > 0) {
            booking.setDepositDeadline(depositDeadline(checkInDate, checkInTime));
        }

        Booking savedBooking = bookingRepository.save(booking);
        emailService.sendBookingConfirmation(savedBooking);
        return savedBooking;
    }

    // Han chuyen coc = luc dat + so gio cai dat, nhung khong muon hon 6 tieng truoc gio nhan phong
    // (moc tu dong huy don chua xac nhan cua BookingExpirationScheduler)
    private LocalDateTime depositDeadline(LocalDate checkInDate, LocalTime checkInTime) {
        LocalDateTime deadline = LocalDateTime.now().plusHours(pricingService.getSettings().getDepositDeadlineHours());
        LocalDateTime latest = LocalDateTime.of(checkInDate, checkInTime).minusHours(EDIT_CUTOFF_HOURS);
        return deadline.isAfter(latest) ? latest : deadline;
    }

    // Kiem tra du lieu + tinh tien (theo tung dem) -> tra ve don PENDING CHUA luu
    private Booking buildBooking(User customer, Long roomId, LocalDate checkInDate, LocalDate checkOutDate,
                                 LocalTime checkInTime, LocalTime checkOutTime, Integer guests,
                                 Long comboId, String discountCodeStr, String guestName, String guestPhone,
                                 String guestEmail) {

        if (guestName == null || guestName.isBlank()) {
            throw new BusinessException("Vui lòng nhập họ tên người nhận phòng");
        }
        if (guestPhone == null || guestPhone.isBlank()) {
            throw new BusinessException("Vui lòng nhập số điện thoại người nhận phòng");
        }
        if (guestEmail == null || guestEmail.isBlank()) {
            throw new BusinessException("Email nguoi nhan phong khong duoc de trong");
        }
        if (!guestEmail.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new BusinessException("Email nguoi nhan phong khong hop le");
        }
        if (checkInDate == null || checkOutDate == null) {
            throw new BusinessException("Vui lòng chọn đầy đủ ngày nhận phòng và ngày trả phòng");
        }
        if (checkInTime == null || checkOutTime == null) {
            throw new BusinessException("Vui lòng chọn đầy đủ giờ nhận phòng và giờ trả phòng");
        }
        // BR-02
        if (!checkOutDate.isAfter(checkInDate)) {
            throw new BusinessException("Ngày trả phòng phải lớn hơn ngày nhận phòng");
        }
        if (checkInDate.isBefore(LocalDate.now())) {
            throw new BusinessException("Ngày nhận phòng không được ở trong quá khứ");
        }

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy phòng"));

        // BR-03
        if (room.getStatus() == RoomStatus.MAINTENANCE) {
            throw new BusinessException("Phòng này đang bảo trì, không thể đặt");
        }

        // BR-01 - kiem tra lai lan cuoi ngay truoc khi luu, phong truong hop phong bi dat mat trong luc khach dang xem
        if (bookingRepository.existsOverlappingBooking(roomId, checkInDate, checkOutDate)) {
            throw new BusinessException("Rất tiếc, phòng này vừa được người khác đặt mất trong khoảng ngày bạn chọn. Vui lòng chọn phòng hoặc ngày khác");
        }

        // Tien phong cong tung dem: ngay thuong / cuoi tuan / ngay le co gia khac nhau
        BigDecimal roomAmount = pricingService.roomAmount(room.getPrice(), checkInDate, checkOutDate);

        Combo combo = null;
        BigDecimal comboAmount = BigDecimal.ZERO;
        if (comboId != null) {
            combo = comboRepository.findById(comboId)
                    .orElseThrow(() -> new BusinessException("Không tìm thấy combo đã chọn"));
            comboAmount = combo.getPrice();
        }

        BigDecimal subtotal = roomAmount.add(comboAmount);

        DiscountCode discountCode = null;
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (discountCodeStr != null && !discountCodeStr.isBlank()) {
            discountCode = discountCodeRepository.findByCodeAndActiveTrue(discountCodeStr.trim().toUpperCase())
                    .orElseThrow(() -> new BusinessException("Mã giảm giá không tồn tại hoặc đã ngừng áp dụng"));

            if (discountCode.getApplicableCustomerType() != null
                    && discountCode.getApplicableCustomerType() != customer.getCustomerType()) {
                throw new BusinessException("Mã giảm giá này không áp dụng cho loại khách hàng của bạn");
            }

            if (bookingRepository.existsByCustomerIdAndDiscountCodeIdAndStatusNot(
                    customer.getId(), discountCode.getId(), BookingStatus.CANCELLED)) {
                throw new BusinessException(DISCOUNT_ALREADY_USED_MESSAGE);
            }

            if (discountCode.getDiscountType() == DiscountType.PERCENTAGE) {
                discountAmount = subtotal.multiply(discountCode.getDiscountValue())
                        .divide(BigDecimal.valueOf(100));
            } else {
                discountAmount = discountCode.getDiscountValue().min(subtotal);
            }
        }

        BigDecimal totalAmount = subtotal.subtract(discountAmount);
        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            totalAmount = BigDecimal.ZERO;
        }

        // Mac dinh 1 khach neu khong nhap (cot number_of_guests khong cho phep null)
        int actualGuests = (guests != null && guests > 0) ? guests : 1;

        return Booking.builder()
                .customer(customer)
                .room(room)
                .checkInDate(checkInDate)
                .checkOutDate(checkOutDate)
                .checkInTime(checkInTime)
                .checkOutTime(checkOutTime)
                .numberOfGuests(actualGuests)
                .guestName(guestName.trim())
                .guestPhone(guestPhone.trim())
                .guestEmail(guestEmail != null ? guestEmail.trim() : null)
                .combo(combo)
                .discountCode(discountCode)
                .discountAmount(discountAmount)
                .roomAmount(roomAmount)
                .totalAmount(totalAmount)
                .depositAmount(BigDecimal.ZERO)
                .status(BookingStatus.PENDING)
                .build();
    }

    public Booking findByIdForCustomer(Long bookingId, Long customerId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy đơn đặt phòng"));
        if (!booking.getCustomer().getId().equals(customerId)) {
            throw new BusinessException("Bạn không có quyền xem đơn đặt phòng này");
        }
        return booking;
    }

    public List<Booking> findAllForCustomer(Long customerId) {
        return bookingRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    // Chi cho sua khi don chua check-in/checkout/huy, va con truoc >= 6 tieng so voi gio nhan phong
    public boolean canModify(Booking booking) {
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CONFIRMED) {
            return false;
        }
        LocalDateTime checkInDateTime = LocalDateTime.of(booking.getCheckInDate(), booking.getCheckInTime());
        return LocalDateTime.now().isBefore(checkInDateTime.minusHours(EDIT_CUTOFF_HOURS));
    }

    @Transactional
    public Booking updateTimes(Long bookingId, Long customerId, LocalTime newCheckInTime, LocalTime newCheckOutTime) {
        Booking booking = findByIdForCustomer(bookingId, customerId);
        if (!canModify(booking)) {
            throw new BusinessException("Không thể sửa giờ nhận/trả phòng — chỉ được sửa trước "
                    + EDIT_CUTOFF_HOURS + " tiếng so với giờ nhận phòng, và đơn phải chưa check-in/hủy");
        }
        if (newCheckInTime == null || newCheckOutTime == null) {
            throw new BusinessException("Vui lòng chọn đầy đủ giờ nhận và trả phòng");
        }
        booking.setCheckInTime(newCheckInTime);
        booking.setCheckOutTime(newCheckOutTime);
        booking.setCheckInTimeChanged(true);
        return bookingRepository.save(booking);
    }

    // Doi hoac huy combo cho booking da tao - tinh lai tien combo va ma giam gia (neu co) theo tong tien moi
    @Transactional
    public Booking updateCombo(Long bookingId, Long customerId, Long newComboId) {
        Booking booking = findByIdForCustomer(bookingId, customerId);
        if (!canModify(booking)) {
            throw new BusinessException("Không thể thay đổi combo — chỉ được sửa trước "
                    + EDIT_CUTOFF_HOURS + " tiếng so với giờ nhận phòng, và đơn phải chưa check-in/hủy");
        }

        // Giu nguyen tien phong da chot luc dat (gia cuoi tuan/le co the da doi sau do)
        BigDecimal roomAmount = booking.getEffectiveRoomAmount();

        Combo newCombo = null;
        BigDecimal comboAmount = BigDecimal.ZERO;
        if (newComboId != null) {
            newCombo = comboRepository.findById(newComboId)
                    .orElseThrow(() -> new BusinessException("Không tìm thấy combo"));
            comboAmount = newCombo.getPrice();
        }

        BigDecimal subtotal = roomAmount.add(comboAmount);

        BigDecimal discountAmount = BigDecimal.ZERO;
        if (booking.getDiscountCode() != null) {
            DiscountCode dc = booking.getDiscountCode();
            if (dc.getDiscountType() == DiscountType.PERCENTAGE) {
                discountAmount = subtotal.multiply(dc.getDiscountValue()).divide(BigDecimal.valueOf(100));
            } else {
                discountAmount = dc.getDiscountValue().min(subtotal);
            }
        }

        BigDecimal totalAmount = subtotal.subtract(discountAmount);
        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            totalAmount = BigDecimal.ZERO;
        }

        booking.setCombo(newCombo);
        booking.setDiscountAmount(discountAmount);
        booking.setTotalAmount(totalAmount);
        // Chua chuyen coc thi tinh lai tien coc theo tong tien moi; da coc roi thi giu nguyen
        if (booking.isDepositRequired() && !booking.isDepositPaid()) {
            booking.setDepositAmount(pricingService.depositFor(totalAmount));
        }

        return bookingRepository.save(booking);
    }

    // Theo dung state flow: chi PENDING hoac CONFIRMED moi duoc chuyen sang CANCELLED
    @Transactional
    public Booking cancelBooking(Long bookingId, Long customerId) {
        Booking booking = findByIdForCustomer(bookingId, customerId);
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessException("Không thể hủy đơn ở trạng thái hiện tại (" + booking.getStatus() + ")");
        }
        booking.setStatus(BookingStatus.CANCELLED);
        Booking cancelledBooking = bookingRepository.save(booking);
        emailService.sendBookingConfirmation(cancelledBooking);
        return cancelledBooking;
    }

    // ===== Cac thao tac danh cho STAFF/ADMIN =====

    public List<Booking> findAllBookings() {
        return bookingRepository.findAllByOrderByCreatedAtDesc();
    }

    public Booking findById(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy đơn đặt phòng"));
    }

    // Staff mo xem chi tiet don -> tat cac canh bao (don moi / doi gio nhan phong) cua don do
    @Transactional
    public Booking findByIdAndMarkSeenByStaff(Long bookingId) {
        Booking booking = findById(bookingId);
        if (booking.isNewBooking() || booking.isCheckInTimeChanged()) {
            booking.setNewBooking(false);
            booking.setCheckInTimeChanged(false);
            bookingRepository.save(booking);
        }
        return booking;
    }

    // So don moi (khach vua dat, staff chua mo xem) - dung de hien cham do o menu admin
    public long countNewBookings() {
        return bookingRepository.countByNewBookingTrue();
    }

    // Nhan vien xac nhan booking: PENDING -> CONFIRMED
    @Transactional
    public Booking confirmBooking(Long bookingId) {
        Booking booking = findById(bookingId);
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BusinessException("Chỉ có thể xác nhận đơn đang ở trạng thái Chờ xác nhận");
        }
        booking.setStatus(BookingStatus.CONFIRMED);
        Booking confirmedBooking = bookingRepository.save(booking);
        emailService.sendBookingConfirmation(confirmedBooking);
        return confirmedBooking;
    }

    // Nhan vien thay tien coc da vao tai khoan -> ghi nhan da coc va xac nhan don luon
    @Transactional
    public Booking confirmDeposit(Long bookingId) {
        Booking booking = findById(bookingId);
        if (!booking.isDepositRequired()) {
            throw new BusinessException("Đơn này không yêu cầu đặt cọc");
        }
        if (booking.isDepositPaid()) {
            throw new BusinessException("Đơn này đã được ghi nhận đặt cọc trước đó");
        }
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BusinessException("Chỉ ghi nhận đặt cọc cho đơn đang ở trạng thái Chờ xác nhận");
        }
        booking.setDepositPaidAt(LocalDateTime.now());
        booking.setStatus(BookingStatus.CONFIRMED);
        Booking confirmedBooking = bookingRepository.save(booking);
        emailService.sendBookingConfirmation(confirmedBooking);
        return confirmedBooking;
    }

    // BR-04: chi booking da CONFIRMED moi duoc check-in
    // Khai bao luu tru: phai nhap giay to it nhat 1 khach truoc khi cho nhan phong
    @Transactional
    public Booking checkIn(Long bookingId) {
        Booking booking = findById(bookingId);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessException("Chỉ có thể check-in đơn đã được xác nhận (CONFIRMED)");
        }
        if (guestRepository.countByBookingId(bookingId) == 0) {
            throw new BusinessException("Vui lòng khai báo lưu trú (nhập giấy tờ ít nhất 1 khách) trước khi check-in");
        }
        booking.setStatus(BookingStatus.CHECKED_IN);

        Room room = booking.getRoom();
        room.setStatus(RoomStatus.OCCUPIED);
        roomRepository.save(room);

        Booking checkedInBooking = bookingRepository.save(booking);
        emailService.sendBookingConfirmation(checkedInBooking);
        return checkedInBooking;
    }

    // BR-05: chi booking da CHECKED_IN moi duoc check-out
    // Sau khi check-out, phong chuyen sang NOT_READY - can nhan vien/admin xac nhan da don dep xong
    @Transactional
    public Booking checkOut(Long bookingId) {
        Booking booking = findById(bookingId);
        if (booking.getStatus() != BookingStatus.CHECKED_IN) {
            throw new BusinessException("Chỉ có thể check-out đơn đã check-in (CHECKED_IN)");
        }
        booking.setStatus(BookingStatus.CHECKED_OUT);

        Room room = booking.getRoom();
        room.setStatus(RoomStatus.NOT_READY);
        roomRepository.save(room);

        Booking checkedOutBooking = bookingRepository.save(booking);
        emailService.sendBookingConfirmation(checkedOutBooking);
        return checkedOutBooking;
    }

    // Dat phong truc tiep tai quay cho khach vang lai (khong can dang ky tai khoan truoc),
    // nhan vien xac nhan luon (CONFIRMED) vi da lam viec truc tiep voi khach
    @Transactional
    public Booking createWalkInBooking(User customer, Long roomId, LocalDate checkInDate, LocalDate checkOutDate,
                                        LocalTime checkInTime, LocalTime checkOutTime, Integer guests,
                                        Long comboId, String guestName, String guestPhone, String guestEmail) {

        Booking booking = buildBooking(customer, roomId, checkInDate, checkOutDate, checkInTime, checkOutTime,
                guests, comboId, null, guestName, guestPhone, guestEmail);
        // Khach o ngay tai quay -> khong can dat coc, xac nhan luon
        booking.setStatus(BookingStatus.CONFIRMED);
        // Nhan vien tu tay tao don nay nen khong can bao "don moi"
        booking.setNewBooking(false);
        Booking savedBooking = bookingRepository.save(booking);
        emailService.sendBookingConfirmation(savedBooking);
        return savedBooking;
    }

    // Nhan vien/admin huy don thay khach (VD: khach goi dien nho huy) - khong kiem tra chu don nhu ben customer
    @Transactional
    public Booking cancelBookingByStaff(Long bookingId) {
        Booking booking = findById(bookingId);
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessException("Không thể hủy đơn ở trạng thái hiện tại (" + booking.getStatus().getVietnameseLabel() + ")");
        }
        booking.setStatus(BookingStatus.CANCELLED);
        Booking cancelledBooking = bookingRepository.save(booking);
        emailService.sendBookingConfirmation(cancelledBooking);
        return cancelledBooking;
    }

    // Loc danh sach booking theo trang thai, dung cho man hinh quan ly cua STAFF/ADMIN
    public List<Booking> findAllBookings(BookingStatus statusFilter) {
        return findAllBookings(statusFilter, null);
    }

    // Loc theo trang thai va/hoac tim theo ten, email, sdt khach hang (khong phan biet hoa thuong)
    public List<Booking> findAllBookings(BookingStatus statusFilter, String search) {
        List<Booking> all = bookingRepository.findAllByOrderByCreatedAtDesc();
        if (statusFilter != null) {
            all = all.stream().filter(b -> b.getStatus() == statusFilter).toList();
        }
        if (search != null && !search.isBlank()) {
            String keyword = search.trim().toLowerCase();
            all = all.stream().filter(b -> matchesSearch(b, keyword)).toList();
        }
        return all;
    }

    // Chi khop theo gia tri THUC SU hien thi tren bang (uu tien thong tin nguoi nhan phong,
    // fallback ve thong tin tai khoan neu don khong co) - tranh khop nham khi 1 khach hang dat nhieu don
    // voi thong tin nguoi nhan phong khac voi so dien thoai/email dang ky tai khoan
    private boolean matchesSearch(Booking b, String keyword) {
        String name = b.getGuestName() != null ? b.getGuestName() : b.getCustomer().getFullName();
        String phone = b.getGuestPhone() != null ? b.getGuestPhone() : b.getCustomer().getPhoneNumber();
        String email = b.getGuestEmail() != null ? b.getGuestEmail() : b.getCustomer().getEmail();
        return containsIgnoreCase(name, keyword)
                || containsIgnoreCase(phone, keyword)
                || containsIgnoreCase(email, keyword);
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    // ===== Thong ke cho bieu do dashboard admin - loc theo khoang ngay tao don (createdAt) =====

    // Bieu do tron: so luong don theo tung trang thai, trong khoang [fromDate, toDate]
    public Map<BookingStatus, Long> getBookingStatusCounts(LocalDate fromDate, LocalDate toDate) {
        Map<BookingStatus, Long> counts = new LinkedHashMap<>();
        for (BookingStatus status : BookingStatus.values()) {
            counts.put(status, 0L);
        }
        for (Booking booking : findAllBookings()) {
            if (isWithinRange(booking, fromDate, toDate)) {
                counts.merge(booking.getStatus(), 1L, Long::sum);
            }
        }
        return counts;
    }

    // Bieu do cot: doanh thu theo tung loai phong trong khoang [fromDate, toDate] (bo qua don da huy)
    // Doanh thu 1 don = tien don (phong + combo - giam gia) + phu phi phat sinh khi o
    public Map<String, BigDecimal> getRevenueByRoomType(LocalDate fromDate, LocalDate toDate) {
        Map<String, BigDecimal> revenue = new LinkedHashMap<>();
        Map<Long, BigDecimal> charges = chargeTotalsByBooking();
        for (Booking booking : findAllBookings()) {
            if (booking.getStatus() == BookingStatus.CANCELLED || !isWithinRange(booking, fromDate, toDate)) {
                continue;
            }
            String typeName = booking.getRoom().getRoomType().getName();
            revenue.merge(typeName, revenueOf(booking, charges), BigDecimal::add);
        }
        return revenue;
    }

    // Bieu do duong: doanh thu theo tung ngay trong khoang [fromDate, toDate] neu <= 31 ngay,
    // neu khoang dai hon thi gom theo thang de bieu do khong bi qua nhieu diem (bo qua don da huy)
    public Map<String, BigDecimal> getRevenueTrend(LocalDate fromDate, LocalDate toDate) {
        Map<String, BigDecimal> revenue = new LinkedHashMap<>();
        long totalDays = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        boolean groupByDay = totalDays <= 31;
        DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("dd/MM");
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MM/yyyy");

        if (groupByDay) {
            for (LocalDate d = fromDate; !d.isAfter(toDate); d = d.plusDays(1)) {
                revenue.put(d.format(dayFormatter), BigDecimal.ZERO);
            }
        } else {
            YearMonth startMonth = YearMonth.from(fromDate);
            YearMonth endMonth = YearMonth.from(toDate);
            for (YearMonth m = startMonth; !m.isAfter(endMonth); m = m.plusMonths(1)) {
                revenue.put(m.format(monthFormatter), BigDecimal.ZERO);
            }
        }

        Map<Long, BigDecimal> charges = chargeTotalsByBooking();
        for (Booking booking : findAllBookings()) {
            if (booking.getStatus() == BookingStatus.CANCELLED || !isWithinRange(booking, fromDate, toDate)) {
                continue;
            }
            LocalDate createdDate = booking.getCreatedAt().toLocalDate();
            String label = groupByDay ? createdDate.format(dayFormatter) : YearMonth.from(createdDate).format(monthFormatter);
            revenue.computeIfPresent(label, (key, existing) -> existing.add(revenueOf(booking, charges)));
        }
        return revenue;
    }

    // ===== Chi so khach san - tinh theo ngay khach O (dem luu tru), khong theo ngay tao don =====

    // Don duoc tinh la da ban phong: da xac nhan / dang o / da tra phong (bo qua don cho duyet va don huy)
    private static final Set<BookingStatus> ROOM_SOLD_STATUSES =
            EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN, BookingStatus.CHECKED_OUT);

    // occupancyRate: % dem-phong da ban / tong dem-phong co the ban (so phong x so ngay)
    // adr: gia trung binh moi dem-phong da ban; revPar: doanh thu tren moi dem-phong co the ban
    public record HotelKpi(double occupancyRate, BigDecimal adr, BigDecimal revPar,
                           long soldRoomNights, long availableRoomNights) {
    }

    // Tien don (phong + combo - giam gia, khong gom phu phi) chia deu cho tung dem cua don,
    // chi cong cac dem nam trong khoang [fromDate, toDate]
    public HotelKpi getHotelKpi(LocalDate fromDate, LocalDate toDate) {
        long days = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        long availableRoomNights = roomRepository.count() * days;
        LocalDate rangeEnd = toDate.plusDays(1);

        long soldRoomNights = 0;
        BigDecimal roomRevenue = BigDecimal.ZERO;
        for (Booking booking : findAllBookings()) {
            if (!ROOM_SOLD_STATUSES.contains(booking.getStatus())) {
                continue;
            }
            long nights = ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
            LocalDate start = booking.getCheckInDate().isAfter(fromDate) ? booking.getCheckInDate() : fromDate;
            LocalDate end = booking.getCheckOutDate().isBefore(rangeEnd) ? booking.getCheckOutDate() : rangeEnd;
            long nightsInRange = ChronoUnit.DAYS.between(start, end);
            if (nights <= 0 || nightsInRange <= 0) {
                continue;
            }
            soldRoomNights += nightsInRange;
            roomRevenue = roomRevenue.add(booking.getTotalAmount().multiply(BigDecimal.valueOf(nightsInRange))
                    .divide(BigDecimal.valueOf(nights), 0, RoundingMode.HALF_UP));
        }

        double occupancyRate = availableRoomNights == 0 ? 0
                : Math.round(soldRoomNights * 1000.0 / availableRoomNights) / 10.0;
        BigDecimal adr = soldRoomNights == 0 ? BigDecimal.ZERO
                : roomRevenue.divide(BigDecimal.valueOf(soldRoomNights), 0, RoundingMode.HALF_UP);
        BigDecimal revPar = availableRoomNights == 0 ? BigDecimal.ZERO
                : roomRevenue.divide(BigDecimal.valueOf(availableRoomNights), 0, RoundingMode.HALF_UP);
        return new HotelKpi(occupancyRate, adr, revPar, soldRoomNights, availableRoomNights);
    }

    // Don tao trong khoang [fromDate, toDate] - cung co so voi cac bieu do, dung cho file Excel
    public List<Booking> findBookingsCreatedBetween(LocalDate fromDate, LocalDate toDate) {
        return findAllBookings().stream().filter(b -> isWithinRange(b, fromDate, toDate)).toList();
    }

    private BigDecimal revenueOf(Booking booking, Map<Long, BigDecimal> charges) {
        return booking.getTotalAmount().add(charges.getOrDefault(booking.getId(), BigDecimal.ZERO));
    }

    // Tong phu phi theo ma don (don khong co phu phi thi khong co trong map)
    public Map<Long, BigDecimal> chargeTotalsByBooking() {
        Map<Long, BigDecimal> totals = new HashMap<>();
        for (Object[] row : chargeRepository.sumAmountGroupByBooking()) {
            totals.put((Long) row[0], (BigDecimal) row[1]);
        }
        return totals;
    }

    private boolean isWithinRange(Booking booking, LocalDate fromDate, LocalDate toDate) {
        LocalDate createdDate = booking.getCreatedAt().toLocalDate();
        return !createdDate.isBefore(fromDate) && !createdDate.isAfter(toDate);
    }
}
