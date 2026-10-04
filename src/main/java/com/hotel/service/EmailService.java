package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

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
