package com.hotel.service;

import com.hotel.entity.*;
import com.hotel.exception.BusinessException;
import com.hotel.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

// Thanh toan khi khach tra phong (check-out): tien mat / chuyen khoan (QR) / the (chua ho tro)
@Service
public class PaymentService {

    // Noi dung chuyen khoan kem ma don de nhan vien doi chieu tien vao la cua don nao, VD: "HB15"
    private static final String TRANSFER_PREFIX = "HB";

    private final PaymentRepository paymentRepository;
    private final BookingService bookingService;
    private final EmailService emailService;

    @Value("${app.payment.bank-id:MB}")
    private String bankId;

    @Value("${app.payment.account-no:0000000000}")
    private String accountNo;

    @Value("${app.payment.account-name:KHACH SAN HOTEL BOOKING}")
    private String accountName;

    public PaymentService(PaymentRepository paymentRepository, BookingService bookingService, EmailService emailService) {
        this.paymentRepository = paymentRepository;
        this.bookingService = bookingService;
        this.emailService = emailService;
    }

    public Optional<Payment> findByBookingId(Long bookingId) {
        return paymentRepository.findByBookingId(bookingId);
    }

    // Tra ve map bookingId -> Payment, dung cho cac trang danh sach (tranh truy van tung don)
    public Map<Long, Payment> findByBookings(Collection<Booking> bookings) {
        if (bookings.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = bookings.stream().map(Booking::getId).toList();
        return paymentRepository.findByBookingIdIn(ids).stream()
                .collect(Collectors.toMap(p -> p.getBooking().getId(), Function.identity()));
    }

    public boolean isPaid(Long bookingId) {
        return findByBookingId(bookingId).map(p -> p.getStatus() == PaymentStatus.PAID).orElse(false);
    }

    public String transferContent(Booking booking) {
        return TRANSFER_PREFIX + booking.getId();
    }

    // Anh QR VietQR (chuan Napas) - app ngan hang nao quet cung tu dien san so tien va noi dung chuyen khoan
    public String buildQrUrl(Booking booking) {
        return "https://img.vietqr.io/image/" + bankId + "-" + accountNo + "-compact2.png"
                + "?amount=" + booking.getTotalAmount().setScale(0, RoundingMode.HALF_UP).toPlainString()
                + "&addInfo=" + URLEncoder.encode(transferContent(booking), StandardCharsets.UTF_8)
                + "&accountName=" + URLEncoder.encode(accountName, StandardCharsets.UTF_8);
    }

    public String getBankId() {
        return bankId;
    }

    public String getAccountNo() {
        return accountNo;
    }

    public String getAccountName() {
        return accountName;
    }

    // Nhan vien kiem tra app ngan hang thay tien da vao -> ghi nhan da thanh toan chuyen khoan
    @Transactional
    public Payment confirmBankTransfer(Long bookingId) {
        Booking booking = bookingService.findById(bookingId);
        if (booking.getStatus() != BookingStatus.CHECKED_IN) {
            throw new BusinessException("Chỉ ghi nhận thanh toán cho đơn đang ở trạng thái Đã nhận phòng");
        }
        return markPaid(booking, PaymentMethod.BANK_TRANSFER);
    }

    // Hoan tat check-out kem thanh toan:
    // - Tien mat: nhan vien thu tien tai quay -> ghi nhan PAID luon
    // - Chuyen khoan: phai co xac nhan da nhan tien truoc (QR da hien "thanh cong")
    // - The: chua ho tro
    @Transactional
    public Booking checkOutWithPayment(Long bookingId, PaymentMethod method) {
        if (method == null) {
            throw new BusinessException("Vui lòng chọn phương thức thanh toán");
        }
        Booking booking = bookingService.findById(bookingId);
        if (booking.getStatus() != BookingStatus.CHECKED_IN) {
            throw new BusinessException("Chỉ có thể check-out đơn đã check-in (CHECKED_IN)");
        }
        // Da nhan chuyen khoan truoc do thi khong thu them lan nua
        if (!isPaid(bookingId)) {
            switch (method) {
                case CASH -> markPaid(booking, PaymentMethod.CASH);
                case BANK_TRANSFER -> throw new BusinessException(
                        "Chưa nhận được tiền chuyển khoản cho đơn này. Vui lòng chờ khách chuyển khoản thành công");
                default -> throw new BusinessException("Phương thức thanh toán bằng thẻ hiện chưa được hỗ trợ");
            }
        }
        Booking checkedOut = bookingService.checkOut(bookingId);
        // Gui hoa don cho khach qua email (thay cho email bao "da check-out" dang chu thuong)
        findByBookingId(bookingId).ifPresent(payment -> emailService.sendInvoice(checkedOut, payment));
        return checkedOut;
    }

    // Nhan vien bam "Gui lai hoa don" - tra ve email da gui toi
    @Transactional(readOnly = true)
    public String resendInvoice(Long bookingId) {
        Booking booking = bookingService.findById(bookingId);
        Payment payment = findByBookingId(bookingId)
                .filter(p -> p.getStatus() == PaymentStatus.PAID)
                .orElseThrow(() -> new BusinessException("Đơn chưa được thanh toán nên chưa có hóa đơn để gửi"));
        if (booking.getGuestEmail() == null || booking.getGuestEmail().isBlank()) {
            throw new BusinessException("Đơn này không có email người nhận phòng");
        }
        if (!emailService.sendInvoice(booking, payment)) {
            throw new BusinessException("Gửi email thất bại. Kiểm tra cấu hình MAIL_USERNAME / MAIL_PASSWORD");
        }
        return booking.getGuestEmail();
    }

    // Du lieu hien thi hoa don - chi xuat duoc khi don da thanh toan
    public Map<String, Object> buildInvoiceData(Booking booking) {
        Payment payment = findByBookingId(booking.getId())
                .filter(p -> p.getStatus() == PaymentStatus.PAID)
                .orElseThrow(() -> new BusinessException("Đơn chưa được thanh toán nên chưa thể xuất hóa đơn"));
        long nights = ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
        Map<String, Object> data = new HashMap<>();
        data.put("booking", booking);
        data.put("payment", payment);
        data.put("nights", nights);
        data.put("roomAmount", booking.getRoom().getPrice().multiply(BigDecimal.valueOf(nights)));
        data.put("invoiceNo", payment.getInvoiceNo());
        return data;
    }

    // Thong ke tien DA THU theo phuong thuc, loc theo ngay thanh toan trong [fromDate, toDate]
    public Map<PaymentMethod, BigDecimal> getPaidRevenueByMethod(LocalDate fromDate, LocalDate toDate) {
        Map<PaymentMethod, BigDecimal> result = new LinkedHashMap<>();
        result.put(PaymentMethod.CASH, BigDecimal.ZERO);
        result.put(PaymentMethod.BANK_TRANSFER, BigDecimal.ZERO);
        for (Payment p : paymentRepository.findByStatusAndPaymentDateBetween(
                PaymentStatus.PAID, fromDate.atStartOfDay(), toDate.plusDays(1).atStartOfDay().minusNanos(1))) {
            result.merge(p.getPaymentMethod(), p.getAmount(), BigDecimal::add);
        }
        return result;
    }

    private Payment markPaid(Booking booking, PaymentMethod method) {
        Payment payment = paymentRepository.findByBookingId(booking.getId())
                .orElseGet(() -> Payment.builder().booking(booking).build());
        payment.setAmount(booking.getTotalAmount());
        payment.setPaymentMethod(method);
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaymentDate(LocalDateTime.now());
        return paymentRepository.save(payment);
    }
}
