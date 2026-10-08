package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingStatus;
import com.hotel.entity.Payment;
import com.hotel.entity.User;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String senderEmail;
    private final String senderPassword;

    public EmailService(JavaMailSender mailSender,
                        @Value("${spring.mail.username:}") String senderEmail,
                        @Value("${spring.mail.password:}") String senderPassword) {
        this.mailSender = mailSender;
        this.senderEmail = senderEmail;
        this.senderPassword = senderPassword;
    }

    // Gui email xac nhan sau khi dat phong thanh cong. Neu gui that bai (VD: chua cau hinh dung mat khau ung dung)
    // thi CHI ghi log canh bao, KHONG lam hong luong dat phong - dat phong van thanh cong binh thuong.
    public void sendBookingConfirmation(Booking booking) {
        // Khi tra phong khach nhan email hoa don (sendInvoice) thay cho email thong bao trang thai
        if (booking.getStatus() == BookingStatus.CHECKED_OUT) {
            return;
        }
        if (booking.getGuestEmail() == null || booking.getGuestEmail().isBlank()) {
            log.warn("Khong gui email xac nhan cho booking #{} vi email nguoi nhan trong", booking.getId());
            return;
        }
        if (senderEmail == null || senderEmail.isBlank()
                || senderPassword == null || senderPassword.isBlank()) {
            log.error("Khong gui email xac nhan cho booking #{}: chua dat MAIL_USERNAME va MAIL_PASSWORD", booking.getId());
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("Hotel Booking <" + senderEmail + ">");
            message.setTo(booking.getGuestEmail());
            message.setSubject("Xác nhận đặt phòng #" + booking.getId() + " - Hotel Booking");
            message.setText(buildBody(booking));
            message.setSubject(buildSubject(booking));
            mailSender.send(message);
            log.info("Da gui email xac nhan cho booking #{} toi {}", booking.getId(), booking.getGuestEmail());
        } catch (Exception ex) {
            log.error("Gui email xac nhan that bai cho booking #{} toi {}", booking.getId(), booking.getGuestEmail(), ex);
        }
    }

    // Gui lien ket dat lai mat khau. Neu chua cau hinh mail thi ghi lien ket ra log de van thu nghiem duoc.
    public void sendPasswordReset(User user, String resetLink) {
        if (senderEmail == null || senderEmail.isBlank()
                || senderPassword == null || senderPassword.isBlank()) {
            log.warn("Chua dat MAIL_USERNAME va MAIL_PASSWORD - lien ket dat lai mat khau cho {}: {}", user.getEmail(), resetLink);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("Hotel Booking <" + senderEmail + ">");
            message.setTo(user.getEmail());
            message.setSubject("Hotel Booking - Đặt lại mật khẩu");
            message.setText("Xin chào " + user.getFullName() + ",\n\n"
                    + "Chúng tôi đã nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn.\n"
                    + "Nhấn vào liên kết dưới đây để tạo mật khẩu mới (có hiệu lực trong "
                    + UserService.RESET_TOKEN_VALID_MINUTES + " phút):\n\n"
                    + resetLink + "\n\n"
                    + "Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này. Mật khẩu hiện tại vẫn được giữ nguyên.\n\n"
                    + "Trân trọng,\nHotel Booking");
            mailSender.send(message);
            log.info("Da gui email dat lai mat khau toi {}", user.getEmail());
        } catch (Exception ex) {
            log.error("Gui email dat lai mat khau that bai toi {}", user.getEmail(), ex);
        }
    }

    // Gui hoa don thanh toan (HTML) sau khi check-out. Tra ve true neu gui thanh cong.
    // Gui that bai chi ghi log, khong lam hong luong check-out.
    public boolean sendInvoice(Booking booking, Payment payment) {
        if (booking.getGuestEmail() == null || booking.getGuestEmail().isBlank()) {
            log.warn("Khong gui hoa don cho booking #{} vi email nguoi nhan trong", booking.getId());
            return false;
        }
        if (senderEmail == null || senderEmail.isBlank()
                || senderPassword == null || senderPassword.isBlank()) {
            log.error("Khong gui hoa don cho booking #{}: chua dat MAIL_USERNAME va MAIL_PASSWORD", booking.getId());
            return false;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(senderEmail, "Hotel Booking");
            helper.setTo(booking.getGuestEmail());
            helper.setSubject("Hotel Booking - Hóa đơn " + payment.getInvoiceNo() + " (đơn #" + booking.getId() + ")");
            helper.setText(buildInvoiceHtml(booking, payment), true);
            mailSender.send(message);
            log.info("Da gui hoa don {} toi {}", payment.getInvoiceNo(), booking.getGuestEmail());
            return true;
        } catch (Exception ex) {
            log.error("Gui hoa don that bai cho booking #{} toi {}", booking.getId(), booking.getGuestEmail(), ex);
            return false;
        }
    }

    // Email HTML dung inline style (Gmail bo qua the <style>), cung tong trang + xanh baby voi website
    private String buildInvoiceHtml(Booking booking, Payment payment) {
        long nights = ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
        BigDecimal roomAmount = booking.getRoom().getPrice().multiply(BigDecimal.valueOf(nights));
        DateTimeFormatter date = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String paidAt = payment.getPaymentDate() != null
                ? payment.getPaymentDate().format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")) : "";

        StringBuilder rows = new StringBuilder();
        rows.append(invoiceRow("Tiền phòng " + booking.getRoom().getRoomNumber() + " (" + booking.getRoom().getRoomType().getName() + ")",
                money(booking.getRoom().getPrice()) + " x " + nights + " đêm", money(roomAmount)));
        if (booking.getCombo() != null) {
            rows.append(invoiceRow("Combo: " + booking.getCombo().getName(), "1", money(booking.getCombo().getPrice())));
        }
        if (booking.getDiscountCode() != null && booking.getDiscountAmount() != null
                && booking.getDiscountAmount().signum() > 0) {
            rows.append(invoiceRow("Giảm giá (mã " + booking.getDiscountCode().getCode() + ")", "",
                    "-" + money(booking.getDiscountAmount())));
        }

        return """
                <div style="margin:0;padding:24px;background:#f3faff;font-family:Arial,Helvetica,sans-serif;color:#14365a">
                  <div style="max-width:620px;margin:0 auto;background:#ffffff;border:1.5px solid #14365a;border-radius:14px">
                    <div style="padding:22px 26px;background:#9fd8f5;border-bottom:1.5px solid #14365a;border-radius:12px 12px 0 0">
                      <div style="font-size:12px;letter-spacing:2px;font-weight:bold">HOTEL BOOKING</div>
                      <div style="font-size:24px;font-weight:bold;margin-top:4px">Hóa đơn thanh toán</div>
                      <div style="margin-top:6px">Số: <b>%s</b> &middot; Ngày: %s</div>
                    </div>
                    <div style="padding:22px 26px">
                      <p style="margin:0 0 14px">Xin chào <b>%s</b>,</p>
                      <p style="margin:0 0 18px">Cảm ơn bạn đã lưu trú tại Hotel Booking. Dưới đây là hóa đơn cho đơn đặt phòng
                        <b>#%d</b> (nhận phòng %s, trả phòng %s).</p>
                      <table style="width:100%%;border-collapse:collapse;font-size:14px">
                        <tr style="background:#e3f4fd">
                          <th style="padding:10px 12px;text-align:left">Nội dung</th>
                          <th style="padding:10px 12px;text-align:right">Đơn giá</th>
                          <th style="padding:10px 12px;text-align:right">Thành tiền</th>
                        </tr>
                        %s
                        <tr>
                          <td colspan="2" style="padding:14px 12px;text-align:right;font-weight:bold">Tổng thanh toán</td>
                          <td style="padding:14px 12px;text-align:right;font-weight:bold;font-size:18px">%s VND</td>
                        </tr>
                      </table>
                      <p style="margin:18px 0 0">Phương thức: <b>%s</b> &middot; Trạng thái: <b style="color:#1d8a4e">%s</b></p>
                      <p style="margin:22px 0 0">Hẹn gặp lại bạn trong kỳ nghỉ tới!<br>Trân trọng,<br><b>Hotel Booking</b></p>
                    </div>
                  </div>
                </div>
                """.formatted(
                esc(payment.getInvoiceNo()), paidAt,
                esc(booking.getGuestName()),
                booking.getId(), booking.getCheckInDate().format(date), booking.getCheckOutDate().format(date),
                rows,
                money(payment.getAmount()),
                esc(payment.getPaymentMethod().getVietnameseLabel()), esc(payment.getStatus().getVietnameseLabel()));
    }

    private String invoiceRow(String name, String unit, String amount) {
        String cell = "padding:10px 12px;border-bottom:1px dashed #b9dcf0;";
        return "<tr><td style=\"" + cell + "\">" + esc(name) + "</td>"
                + "<td style=\"" + cell + "text-align:right\">" + esc(unit) + "</td>"
                + "<td style=\"" + cell + "text-align:right\">" + esc(amount) + "</td></tr>";
    }

    // Dinh dang tien kieu Viet Nam: 1.250.000
    private String money(BigDecimal value) {
        DecimalFormat format = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(new Locale("vi", "VN")));
        return format.format(value == null ? BigDecimal.ZERO : value);
    }

    private String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }

    private String buildSubject(Booking booking) {
        return switch (booking.getStatus()) {
            case PENDING -> "Hotel Booking - Da nhan yeu cau dat phong #" + booking.getId();
            case CONFIRMED -> "Hotel Booking - Don dat phong #" + booking.getId() + " da duoc xac nhan";
            case CANCELLED -> "Hotel Booking - Don dat phong #" + booking.getId() + " da bi huy";
            case CHECKED_IN -> "Hotel Booking - Ban da check-in booking #" + booking.getId();
            case CHECKED_OUT -> "Hotel Booking - Ban da check-out booking #" + booking.getId();
        };
    }

    private String buildBody(Booking booking) {
        StringBuilder sb = new StringBuilder();
        sb.append("Xin chào ").append(booking.getGuestName()).append(",\n\n");
        sb.append("Cảm ơn bạn đã đặt phòng tại Hotel Booking. Thông tin đơn đặt phòng:\n\n");
        sb.append("Mã đơn: #").append(booking.getId()).append("\n");
        sb.append("Phòng: ").append(booking.getRoom().getRoomNumber())
                .append(" - ").append(booking.getRoom().getRoomType().getName()).append("\n");
        sb.append("Ngày nhận phòng: ").append(booking.getCheckInDate())
                .append(" lúc ").append(booking.getCheckInTime()).append("\n");
        sb.append("Ngày trả phòng: ").append(booking.getCheckOutDate())
                .append(" lúc ").append(booking.getCheckOutTime()).append("\n");
        if (booking.getCombo() != null) {
            sb.append("Combo: ").append(booking.getCombo().getName()).append("\n");
        }
        sb.append("Tổng tiền: ").append(booking.getTotalAmount()).append(" VND\n\n");
        sb.append("Trạng thái đơn: ").append(booking.getStatus().getVietnameseLabel()).append("\n\n");
        sb.append("Trân trọng,\nHotel Booking");
        return sb.toString();
    }
}
