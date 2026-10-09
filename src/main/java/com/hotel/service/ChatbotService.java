package com.hotel.service;

import com.hotel.entity.Combo;
import com.hotel.entity.CustomerType;
import com.hotel.entity.DiscountCode;
import com.hotel.entity.DiscountType;
import com.hotel.entity.Room;
import com.hotel.entity.RoomType;
import com.hotel.repository.ComboRepository;
import com.hotel.repository.DiscountCodeRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.RoomTypeRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Chatbot tu van cho khach hang tren trang chu/danh sach phong.
// KHONG goi AI/LLM ben ngoai - chi nhan dien tu khoa (khong dau) trong cau hoi roi tra loi
// bang du lieu THAT lay truc tiep tu DB (gia phong, phong trong, combo...) de dam bao chinh xac tuyet doi.
@Service
public class ChatbotService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final int AVAILABILITY_LOOKAHEAD_DAYS = 7;

    // Thong tin homestay dung de tu van - de hang so trong code (khong doc tu application.properties)
    // vi Spring doc file .properties bang ISO-8859-1 mac dinh, lam sai lech chu tieng Viet co dau.
    private static final String HOTEL_NAME = "Homestay Mây";
    private static final String HOTEL_ADDRESS = "Dốc Tam Đảo, thị trấn Tam Đảo, Vĩnh Phúc";
    private static final String HOTEL_PHONE = "0338932368";
    private static final String HOTEL_EMAIL = "mayhomestaytd@gmail.com";
    private static final String HOTEL_FACILITIES = "BBQ ngoài trời, Ban công ngắm mây, Đốt lửa trại, Bếp chung, Thuê xe máy, Wi-Fi và chỗ đỗ xe miễn phí";
    private static final String CHECK_IN_TIME = "14h chiều (14:00)";
    private static final String CHECK_OUT_TIME = "12h trưa (12:00)";

    private final RoomTypeRepository roomTypeRepository;
    private final RoomRepository roomRepository;
    private final ComboRepository comboRepository;
    private final DiscountCodeRepository discountCodeRepository;

    public ChatbotService(RoomTypeRepository roomTypeRepository, RoomRepository roomRepository,
                           ComboRepository comboRepository, DiscountCodeRepository discountCodeRepository) {
        this.roomTypeRepository = roomTypeRepository;
        this.roomRepository = roomRepository;
        this.comboRepository = comboRepository;
        this.discountCodeRepository = discountCodeRepository;
    }

    public String answer(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return "Bạn muốn hỏi gì về phòng, giá, đặt cọc, thanh toán, dịch vụ hay combo ạ?";
        }

        String question = normalize(rawMessage);
        List<RoomType> matchedTypes = matchRoomTypes(question);
        List<String> sections = new ArrayList<>();

        if (containsAny(question, "xin chao", "chao ban", "hello", " hi ") || question.equals("hi")) {
            sections.add("Xin chào! Mình là trợ lý ảo của " + HOTEL_NAME + ", có thể tư vấn giá phòng, phòng trống, "
                    + "đặt cọc, thanh toán, dịch vụ, combo ăn uống, địa chỉ và thông tin liên hệ.");
        }

        boolean askPrice = containsAny(question, "gia", "bao nhieu tien", "bao nhieu 1 dem");
        boolean askAvailability = containsAny(question, "trong ngay nao", "con trong", "con phong", "het phong", "trong khong", "ngay nao trong", "conphong");
        boolean askDeposit = containsAny(question, "coc");
        boolean askPayment = containsAny(question, "thanh toan", "tra tien", "hinh thuc thanh toan", "phuong thuc thanh toan");
        boolean askFacilities = containsAny(question, "dich vu", "tien ich", "co so vat chat", "tien nghi",
                "bbq", "ban cong", "ngam may", "lua trai", "bep", "nau an", "thue xe", "xe may", "wifi", "wi fi", "bai do xe", "do xe",
                "khach san co gi", "khach san phuc vu", "homestay co gi", "homestay phuc vu");
        boolean askCombo = containsAny(question, "combo", "do an", "an uong", "buffet");
        boolean askPromotion = containsAny(question, "uu dai", "khuyen mai", "giam gia", "ma giam gia", "voucher", "discount");
        boolean askAddress = containsAny(question, "dia chi", "o dau", "vi tri khach san", "khach san o", "vi tri homestay", "homestay o");
        boolean askContact = containsAny(question, "so dien thoai", "hotline", "lien he", " sdt ", "dien thoai", "email");
        boolean askCheckTime = containsAny(question, "gio nhan", "gio tra", "nhan tra phong", "check in", "check out",
                "may gio nhan", "may gio tra");

        if (askPrice) {
            sections.add(buildPriceAnswer(matchedTypes));
        }
        if (askAvailability) {
            sections.add(buildAvailabilityAnswer(matchedTypes));
        }
        if (askDeposit) {
            sections.add("Hiện tại homestay KHÔNG yêu cầu đặt cọc trước khi đặt phòng online. Quý khách chỉ cần điền thông tin và xác nhận đơn, thanh toán khi nhận phòng hoặc trả phòng tại quầy lễ tân.");
        }
        if (askPayment) {
            sections.add("Homestay hỗ trợ các hình thức thanh toán: Tiền mặt, Chuyển khoản ngân hàng, và Thanh toán online.");
        }
        if (askFacilities) {
            sections.add(buildFacilitiesAnswer(matchedTypes));
        }
        if (askCombo) {
            sections.add(buildComboAnswer());
        }
        if (askPromotion) {
            sections.add(buildPromotionAnswer(question));
        }
        if (askAddress) {
            sections.add("Địa chỉ " + HOTEL_NAME + ": " + HOTEL_ADDRESS + ".");
        }
        if (askContact) {
            sections.add("Bạn có thể liên hệ " + HOTEL_NAME + " qua số điện thoại " + HOTEL_PHONE
                    + " hoặc email " + HOTEL_EMAIL + ".");
        }
        if (askCheckTime) {
            sections.add("Giờ nhận phòng tiêu chuẩn: " + CHECK_IN_TIME + " — Giờ trả phòng tiêu chuẩn: " + CHECK_OUT_TIME
                    + ". Quý khách có thể chọn giờ nhận/trả cụ thể ngay khi đặt phòng trên hệ thống.");
        }

        if (sections.isEmpty()) {
            if (!matchedTypes.isEmpty()) {
                // Hoi ten loai phong nhung khong ro y dinh -> tra loi tong quan gia + phong trong cua loai do
                sections.add(buildPriceAnswer(matchedTypes));
                sections.add(buildAvailabilityAnswer(matchedTypes));
            } else {
                sections.add("Mình có thể giúp bạn tra cứu: giá phòng, phòng còn trống ngày nào, chính sách đặt cọc, "
                        + "hình thức thanh toán, dịch vụ/tiện ích, combo ăn uống, địa chỉ, số điện thoại liên hệ và giờ nhận/trả phòng. "
                        + "Ví dụ bạn có thể hỏi: \"Phòng VIP giá bao nhiêu?\", \"Homestay ở đâu?\" hoặc \"Số điện thoại liên hệ là gì?\"");
            }
        }

        return String.join("\n\n", sections);
    }

    // Khop ten loai phong (VD "Phong VIP") xuat hien trong cau hoi, khong phan biet dau/hoa-thuong
    private List<RoomType> matchRoomTypes(String normalizedQuestion) {
        List<RoomType> matched = new ArrayList<>();
        for (RoomType rt : roomTypeRepository.findAll()) {
            String normName = normalize(rt.getName());
            if (normalizedQuestion.contains(normName)) {
                matched.add(rt);
                continue;
            }
            for (String word : normName.split("\\s+")) {
                if (word.length() >= 3 && !word.equals("phong") && normalizedQuestion.contains(word)) {
                    matched.add(rt);
                    break;
                }
            }
        }
        return matched;
    }

    private String buildPriceAnswer(List<RoomType> matchedTypes) {
        List<RoomType> types = matchedTypes.isEmpty() ? roomTypeRepository.findAll() : matchedTypes;
        if (types.isEmpty()) {
            return "Hiện homestay chưa cập nhật loại phòng nào.";
        }
        StringBuilder sb = new StringBuilder("Giá phòng hiện tại:");
        for (RoomType rt : types) {
            List<Room> rooms = roomRepository.findByRoomTypeId(rt.getId());
            if (rooms.isEmpty()) {
                continue;
            }
            BigDecimal min = rooms.stream().map(Room::getPrice).min(BigDecimal::compareTo).orElse(rt.getBasePrice());
            BigDecimal max = rooms.stream().map(Room::getPrice).max(BigDecimal::compareTo).orElse(rt.getBasePrice());
            sb.append("\n- ").append(rt.getName()).append(": ");
            if (min.compareTo(max) == 0) {
                sb.append(formatVnd(min)).append(" VND/đêm");
            } else {
                sb.append(formatVnd(min)).append(" - ").append(formatVnd(max)).append(" VND/đêm");
            }
        }
        return sb.toString();
    }

    private String buildAvailabilityAnswer(List<RoomType> matchedTypes) {
        List<RoomType> types = matchedTypes.isEmpty() ? roomTypeRepository.findAll() : matchedTypes;
        LocalDate today = LocalDate.now();
        StringBuilder sb = new StringBuilder();
        for (RoomType rt : types) {
            List<String> freeDays = new ArrayList<>();
            for (int i = 0; i < AVAILABILITY_LOOKAHEAD_DAYS; i++) {
                LocalDate checkIn = today.plusDays(i);
                LocalDate checkOut = checkIn.plusDays(1);
                int freeCount = roomRepository.findAvailableRooms(checkIn, checkOut, rt.getId(), null).size();
                if (freeCount > 0) {
                    freeDays.add(checkIn.format(DATE_FMT) + " (" + freeCount + " phòng)");
                }
            }
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(rt.getName()).append(" trong ").append(AVAILABILITY_LOOKAHEAD_DAYS).append(" ngày tới: ");
            sb.append(freeDays.isEmpty() ? "hiện không còn phòng trống." : "còn trống vào " + String.join(", ", freeDays) + ".");
        }
        sb.append("\nĐể biết chính xác ngày bạn cần, vui lòng vào trang \"Danh sách phòng\" và chọn ngày nhận/trả để hệ thống kiểm tra trực tiếp.");
        return sb.toString();
    }

    private String buildFacilitiesAnswer(List<RoomType> matchedTypes) {
        StringBuilder sb = new StringBuilder("Tiện ích chung của ").append(HOTEL_NAME).append(": ")
                .append(HOTEL_FACILITIES).append(".");
        // Neu khach hoi kem ten loai phong cu the, bo sung them tien ich rieng cua loai phong do
        if (!matchedTypes.isEmpty()) {
            for (RoomType rt : matchedTypes) {
                if (rt.getAmenities() != null && !rt.getAmenities().isBlank()) {
                    sb.append("\nTiện ích riêng của ").append(rt.getName()).append(": ").append(rt.getAmenities());
                }
            }
        }
        return sb.toString();
    }

    private String buildComboAnswer() {
        List<Combo> combos = comboRepository.findByActiveTrue();
        if (combos.isEmpty()) {
            return "Hiện homestay chưa có combo dịch vụ ăn uống nào đang áp dụng.";
        }
        StringBuilder sb = new StringBuilder("Các combo dịch vụ hiện có:");
        for (Combo c : combos) {
            sb.append("\n- ").append(c.getName()).append(": ").append(formatVnd(c.getPrice())).append(" VND");
            if (c.getDescription() != null && !c.getDescription().isBlank()) {
                sb.append(" (").append(c.getDescription()).append(")");
            }
        }
        return sb.toString();
    }

    // Hoi "nguoi moi/khach moi/lan dau" -> chi tra loi ma ap dung cho CustomerType.NEW hoac ma dung chung (khong gioi han loai khach)
    private String buildPromotionAnswer(String normalizedQuestion) {
        boolean askingForNew = containsAny(normalizedQuestion, "nguoi moi", "khach moi", "lan dau", "moi dang ky", "khach hang moi");
        List<DiscountCode> relevant = discountCodeRepository.findByActiveTrue().stream()
                .filter(c -> !askingForNew || c.getApplicableCustomerType() == null
                        || c.getApplicableCustomerType() == CustomerType.NEW)
                .toList();

        if (relevant.isEmpty()) {
            return askingForNew
                    ? "Hiện tại chưa có mã ưu đãi riêng cho khách hàng mới, bạn theo dõi trang chủ để cập nhật khuyến mãi mới nhất nhé."
                    : "Hiện homestay chưa có mã giảm giá nào đang áp dụng.";
        }

        StringBuilder sb = new StringBuilder(askingForNew ? "Ưu đãi dành cho khách hàng mới:" : "Các mã ưu đãi đang áp dụng:");
        for (DiscountCode c : relevant) {
            sb.append("\n- ").append(c.getCode()).append(": giảm ");
            if (c.getDiscountType() == DiscountType.PERCENTAGE) {
                sb.append(c.getDiscountValue().stripTrailingZeros().toPlainString()).append("%");
            } else {
                sb.append(formatVnd(c.getDiscountValue())).append(" VND");
            }
            if (c.getApplicableCustomerType() != null) {
                sb.append(" (dành cho ").append(c.getApplicableCustomerType().getVietnameseLabel().toLowerCase()).append(")");
            }
            if (c.getDescription() != null && !c.getDescription().isBlank()) {
                sb.append(" - ").append(c.getDescription());
            }
        }
        sb.append("\nNhập mã tương ứng ở bước thanh toán khi đặt phòng để được áp dụng.");
        return sb.toString();
    }

    private String formatVnd(BigDecimal amount) {
        NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));
        nf.setMaximumFractionDigits(0);
        return nf.format(amount);
    }

    private boolean containsAny(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n)) {
                return true;
            }
        }
        return false;
    }

    // Bo dau tieng Viet + ve chu thuong de so khop tu khoa khong phu thuoc dau/hoa-thuong
    private String normalize(String s) {
        String temp = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        temp = temp.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        temp = temp.replace('đ', 'd').replace('Đ', 'd');
        return " " + temp + " ";
    }
}
