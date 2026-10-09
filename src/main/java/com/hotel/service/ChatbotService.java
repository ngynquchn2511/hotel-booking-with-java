package com.hotel.service;

import com.hotel.entity.Combo;
import com.hotel.entity.CustomerType;
import com.hotel.entity.DiscountCode;
import com.hotel.entity.DiscountType;
import com.hotel.entity.PricingSettings;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// Chatbot "Mây" tu van cho khach tren cac trang khach hang.
// KHONG goi AI/LLM ben ngoai - chi nhan dien tu khoa (khong dau) trong cau hoi roi tra loi
// bang du lieu THAT lay truc tiep tu DB (gia phong, phong trong, combo, cai dat coc...) de dam bao chinh xac.
// Quy uoc van ban tra ve (widget hien thi): dong bat dau bang "• " la 1 muc danh sach, **chu** la chu dam.
@Service
public class ChatbotService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final int AVAILABILITY_LOOKAHEAD_DAYS = 7;

    // Thong tin homestay dung de tu van - de hang so trong code (khong doc tu application.properties)
    // vi Spring doc file .properties bang ISO-8859-1 mac dinh, lam sai lech chu tieng Viet co dau.
    private static final String HOTEL_NAME = "Homestay Mây";
    private static final String HOTEL_ADDRESS = "Dốc Tam Đảo, thị trấn Tam Đảo, Vĩnh Phúc";
    private static final String HOTEL_PHONE = "0338932368";
    private static final String HOTEL_ZALO = "0337196258";
    private static final String HOTEL_EMAIL = "mayhomestaytd@gmail.com";
    private static final String CHECK_IN_TIME = "14:00";
    private static final String CHECK_OUT_TIME = "12:00";
    private static final String DIRECTIONS_URL = "https://www.google.com/maps/dir/?api=1&destination=21.454468,105.6393101";

    // Nut hanh dong hien duoi cau tra loi (link noi bo hoac ben ngoai)
    public record Action(String label, String url) {
    }

    // Cau tra loi day du: noi dung + nut hanh dong + goi y cau hoi tiep theo
    public record Reply(String text, List<Action> actions, List<String> suggestions) {
    }

    private static final Action ACTION_ROOMS = new Action("🛏️ Xem phòng & đặt ngay", "/rooms");
    private static final Action ACTION_DIRECTIONS = new Action("🧭 Chỉ đường tới Mây", DIRECTIONS_URL);
    private static final Action ACTION_ZALO = new Action("💬 Nhắn Zalo", "https://zalo.me/" + HOTEL_ZALO);
    private static final Action ACTION_CALL = new Action("📞 Gọi hotline", "tel:" + HOTEL_PHONE);
    private static final Action ACTION_EXPLORE = new Action("🗺️ Khám phá Tam Đảo", "/about#kham-pha");
    private static final Action ACTION_HISTORY = new Action("📋 Lịch sử đặt phòng", "/customer/bookings");

    private final RoomTypeRepository roomTypeRepository;
    private final RoomRepository roomRepository;
    private final ComboRepository comboRepository;
    private final DiscountCodeRepository discountCodeRepository;
    private final PricingService pricingService;

    public ChatbotService(RoomTypeRepository roomTypeRepository, RoomRepository roomRepository,
                          ComboRepository comboRepository, DiscountCodeRepository discountCodeRepository,
                          PricingService pricingService) {
        this.roomTypeRepository = roomTypeRepository;
        this.roomRepository = roomRepository;
        this.comboRepository = comboRepository;
        this.discountCodeRepository = discountCodeRepository;
        this.pricingService = pricingService;
    }

    public String answer(String rawMessage) {
        return reply(rawMessage).text();
    }

    public Reply reply(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return new Reply("Bạn muốn hỏi Mây điều gì nè? ☁️ Giá phòng, phòng trống, đặt cọc, combo hay đường lên Tam Đảo đều được nha!",
                    List.of(), List.of("Giá phòng thế nào", "Còn phòng trống không", "Đường lên Tam Đảo đi thế nào"));
        }

        String q = normalize(rawMessage);
        List<RoomType> matchedTypes = matchRoomTypes(q);
        List<String> sections = new ArrayList<>();
        Set<Action> actions = new LinkedHashSet<>();
        Set<String> suggestions = new LinkedHashSet<>();

        boolean greet = containsAny(q, "xin chao", "chao ban", "chao may", "hello", " hi ", " alo ", " hey ") || q.isBlank();
        boolean thanks = containsAny(q, "cam on", "thank", "tks", "cam ta");
        boolean bye = containsAny(q, "tam biet", " bye", "hen gap lai");
        boolean askPrice = containsAny(q, "gia", "bao nhieu tien", "bao nhieu 1 dem", "mat bao nhieu");
        boolean askAvailability = containsAny(q, "trong ngay nao", "con trong", "con phong", "het phong", "trong khong",
                "ngay nao trong", "conphong", "phong trong");
        boolean askDeposit = containsAny(q, "coc");
        boolean askPayment = containsAny(q, "thanh toan", "tra tien", "chuyen khoan", "tien mat", "quet qr");
        boolean askFacilities = containsAny(q, "dich vu", "tien ich", "co so vat chat", "tien nghi",
                "bbq", "ban cong", "ngam may", "lua trai", "bep", "nau an", "thue xe", "xe may", "wifi", "wi fi",
                "bai do xe", "do xe", "khach san co gi", "khach san phuc vu", "homestay co gi", "homestay phuc vu");
        boolean askCombo = containsAny(q, "combo", "do an", "an uong", "buffet", "an lau", "mon lau", "nuong");
        boolean askPromotion = containsAny(q, "uu dai", "khuyen mai", "giam gia", "ma giam", "voucher", "discount");
        boolean askDirections = containsAny(q, "chi duong", "duong di", "duong len", "di nhu the nao", "di the nao",
                "tu ha noi", "bao xa", "bao lau", "xe khach", "di xe gi");
        boolean askAddress = !askDirections && containsAny(q, "dia chi", "o dau", "vi tri", "khach san o", "homestay o", "ban do");
        boolean askContact = containsAny(q, "so dien thoai", "hotline", "lien he", " sdt ", "dien thoai", "email", "zalo", "goi dien");
        boolean askCheckTime = containsAny(q, "gio nhan", "gio tra", "nhan tra phong", "check in", "check out", "checkin",
                "checkout", "may gio nhan", "may gio tra");
        boolean askWeather = containsAny(q, "thoi tiet", "lanh khong", "nhiet do", "mua khong", "suong mu", "mang ao");
        boolean askExplore = containsAny(q, "di choi", "tham quan", "dia diem", "song ao", "chup anh",
                "thac bac", "lau dai", "co gi choi", "choi gi");
        boolean askFood = containsAny(q, "dac san", "an gi", "mon ngon", "quan an", "su su");
        boolean askHowToBook = containsAny(q, "cach dat", "dat phong nhu the nao", "dat phong the nao", "lam sao dat",
                "huong dan dat", "dat the nao", "muon dat phong");
        boolean askCancel = containsAny(q, "huy phong", "huy don", "huy dat", "doi ngay", "hoan coc", "hoan tien");

        if (greet) {
            sections.add("Xin chào bạn! Mình là **Mây** ☁️ — trợ lý nhỏ của " + HOTEL_NAME + " trên Dốc Tam Đảo. "
                    + "Bạn cần Mây giúp gì nè? Hỏi Mây về giá phòng, phòng trống, đặt cọc, combo nướng lẩu "
                    + "hay đường lên Tam Đảo đều được nha 🥰");
            suggestions.add("Giá phòng thế nào");
            suggestions.add("Còn phòng trống không");
        }
        if (askPrice) {
            sections.add(buildPriceAnswer(matchedTypes));
            actions.add(ACTION_ROOMS);
            suggestions.add("Còn phòng trống không");
            suggestions.add("Có cần đặt cọc không");
        }
        if (askAvailability) {
            sections.add(buildAvailabilityAnswer(matchedTypes));
            actions.add(ACTION_ROOMS);
            suggestions.add("Cách đặt phòng thế nào");
        }
        if (askDeposit) {
            sections.add(buildDepositAnswer());
            suggestions.add("Thanh toán bằng cách nào");
            suggestions.add("Hủy phòng thì sao");
        }
        if (askPayment) {
            sections.add("Bạn thanh toán bằng **tiền mặt** hoặc **chuyển khoản** (quét mã QR, số tiền điền sẵn) khi trả phòng nha 💳 "
                    + "Nếu đã đặt cọc thì Mây trừ luôn tiền cọc, bạn chỉ trả phần còn lại thôi.");
            suggestions.add("Có cần đặt cọc không");
        }
        if (askFacilities) {
            sections.add(buildFacilitiesAnswer(matchedTypes));
            suggestions.add("Có combo ăn uống không");
            suggestions.add("Gần đây có chỗ nào đi chơi");
        }
        if (askCombo) {
            sections.add(buildComboAnswer());
            suggestions.add("Lên Tam Đảo ăn gì ngon");
        }
        if (askPromotion) {
            sections.add(buildPromotionAnswer(q));
            actions.add(ACTION_ROOMS);
        }
        if (askDirections) {
            sections.add("Từ Hà Nội lên Tam Đảo khoảng **80–85 km**, đi ô tô hoặc xe máy mất tầm **2 tiếng** 🚗 "
                    + "Đoạn cuối là đường đèo quanh co, bạn đi chậm và bật đèn khi có sương mù nha. "
                    + "Bấm nút bên dưới để Google Maps dẫn đường từ chỗ bạn tới thẳng Mây luôn!");
            actions.add(ACTION_DIRECTIONS);
            suggestions.add("Thời tiết Tam Đảo thế nào");
        }
        if (askAddress) {
            sections.add("Mây ở **" + HOTEL_ADDRESS + "** 📍 — ngay giữa thị trấn trong mây luôn đó!");
            actions.add(ACTION_DIRECTIONS);
            suggestions.add("Đường lên Tam Đảo đi thế nào");
        }
        if (askContact) {
            sections.add("Bạn liên hệ Mây qua mấy cách này nha:\n"
                    + "• Hotline: **" + HOTEL_PHONE + "**\n"
                    + "• Zalo: **" + HOTEL_ZALO + "**\n"
                    + "• Email: " + HOTEL_EMAIL + "\n"
                    + "Nhắn Zalo là Mây trả lời nhanh nhất đó 💬");
            actions.add(ACTION_ZALO);
            actions.add(ACTION_CALL);
        }
        if (askCheckTime) {
            sections.add("⏰ Giờ nhận phòng: **" + CHECK_IN_TIME + "** · Giờ trả phòng: **" + CHECK_OUT_TIME + "**\n"
                    + "Muốn đến sớm hay về muộn hơn, bạn chọn giờ khác lúc đặt phòng nha. "
                    + "Nếu hôm đó có khách trước/sau, Mây sẽ báo giờ phù hợp cho bạn.");
            suggestions.add("Cách đặt phòng thế nào");
        }
        if (askWeather) {
            sections.add("Mây chưa xem được dự báo thời tiết hôm nay 🙈 Nhưng Tam Đảo mát quanh năm, "
                    + "sáng tối se lạnh và hay có sương mù lãng đãng — bạn nhớ mang theo áo khoác nhẹ nha 🧥 "
                    + "Mùa đông (khoảng tháng 12 – 2) có hôm lạnh lắm, nhớ mang đồ thật ấm đó!");
            suggestions.add("Gần đây có chỗ nào đi chơi");
        }
        if (askExplore) {
            sections.add("Quanh Mây có nhiều chỗ xinh để đi lắm nè 🗺️\n"
                    + "• **Thác Bạc** — dòng thác trắng xoá giữa rừng già\n"
                    + "• **Lâu đài Tam Đảo** — góc check-in kiểu châu Âu\n"
                    + "• **Đền Bà Chúa Thượng Ngàn** trên đỉnh Thiên Thị\n"
                    + "• Ngắm **ba đỉnh Tam Đảo** nhấp nhô trong mây\n"
                    + "• **Vườn su su** phủ sương buổi sáng\n"
                    + "Mây có cho thuê xe máy để bạn đi cho tiện nha 🛵");
            actions.add(ACTION_EXPLORE);
            suggestions.add("Lên Tam Đảo ăn gì ngon");
        }
        if (askFood) {
            sections.add("Lên Tam Đảo nhớ thử **su su xào tỏi**, **gà đồi nướng** và mấy món rau rừng nha 😋 "
                    + "Tối về Mây đặt combo nướng hoặc lẩu, quây quần bên bếp than cho ấm bụng là hết ý!");
            suggestions.add("Có combo ăn uống không");
        }
        if (askHowToBook) {
            sections.add("Đặt phòng ở Mây dễ lắm nè 📝\n"
                    + "• Vào **Danh sách phòng**, chọn phòng bạn thích\n"
                    + "• Chọn ngày nhận và ngày trả trên lịch\n"
                    + "• Điền họ tên, số điện thoại, email rồi bấm xác nhận\n"
                    + "• Nếu cần cọc, quét mã QR để chuyển cọc giữ phòng\n"
                    + "Xong là có email xác nhận gửi tới bạn liền!");
            actions.add(ACTION_ROOMS);
            suggestions.add("Có cần đặt cọc không");
        }
        if (askCancel) {
            sections.add("Bạn đăng nhập → **Lịch sử đặt phòng** → mở đơn → bấm **Hủy đặt phòng** "
                    + "(khi đơn chưa nhận phòng) nha. Nếu đã chuyển cọc thì Mây sẽ liên hệ bạn về việc hoàn cọc. "
                    + "Cần gấp thì nhắn Zalo cho Mây là nhanh nhất 💬");
            actions.add(ACTION_HISTORY);
            actions.add(ACTION_ZALO);
        }
        if (thanks) {
            sections.add("Không có gì đâu nè 🥰 Chúc bạn có chuyến đi thật vui. Cần gì cứ gọi Mây nha!");
        }
        if (bye) {
            sections.add("Tạm biệt bạn 👋 Hẹn gặp bạn ở Tam Đảo nha ☁️");
        }

        if (sections.isEmpty()) {
            if (!matchedTypes.isEmpty()) {
                // Chi nhac ten loai phong -> tra loi tong quan gia + phong trong cua loai do
                sections.add(buildPriceAnswer(matchedTypes));
                sections.add(buildAvailabilityAnswer(matchedTypes));
                actions.add(ACTION_ROOMS);
            } else {
                sections.add("Ui, câu này Mây chưa hiểu lắm 🥺 Bạn thử hỏi kiểu:\n"
                        + "• \"Phòng VIP giá bao nhiêu?\"\n"
                        + "• \"Cuối tuần còn phòng không?\"\n"
                        + "• \"Đường lên Tam Đảo đi thế nào?\"\n"
                        + "Hoặc nhắn Zalo để người thật của Mây trả lời bạn nha 💬");
                actions.add(ACTION_ZALO);
                suggestions.add("Giá phòng thế nào");
                suggestions.add("Có cần đặt cọc không");
                suggestions.add("Gần đây có chỗ nào đi chơi");
            }
        }

        return new Reply(String.join("\n\n", sections), List.copyOf(actions),
                suggestions.stream().limit(3).toList());
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
            for (String word : normName.trim().split("\\s+")) {
                if (word.length() >= 3 && !word.equals("phong") && normalizedQuestion.contains(" " + word + " ")) {
                    matched.add(rt);
                    break;
                }
            }
        }
        return matched;
    }

    private String buildPriceAnswer(List<RoomType> matchedTypes) {
        List<RoomType> types = matchedTypes.isEmpty() ? roomTypeRepository.findAll() : matchedTypes;
        StringBuilder sb = new StringBuilder("Giá phòng ở Mây nè 💸");
        boolean any = false;
        for (RoomType rt : types) {
            List<Room> rooms = roomRepository.findByRoomTypeId(rt.getId());
            if (rooms.isEmpty()) {
                continue;
            }
            any = true;
            BigDecimal min = rooms.stream().map(Room::getPrice).min(BigDecimal::compareTo).orElse(rt.getBasePrice());
            BigDecimal max = rooms.stream().map(Room::getPrice).max(BigDecimal::compareTo).orElse(rt.getBasePrice());
            sb.append("\n• ").append(rt.getName()).append(": **");
            sb.append(min.compareTo(max) == 0 ? formatVnd(min) : formatVnd(min) + " – " + formatVnd(max));
            sb.append("đ**/đêm");
        }
        if (!any) {
            return "Mây chưa cập nhật giá phòng lên đây 🥲 Bạn nhắn Zalo để Mây báo giá liền nha!";
        }
        int weekend = settings().getWeekendSurchargePercent();
        if (weekend > 0) {
            sb.append("\nĐêm thứ 6, thứ 7 phụ thu **+").append(weekend).append("%**, dịp lễ Tết có giá riêng nha.");
        }
        sb.append("\nBạn chọn ngày trong trang phòng là thấy giá chính xác từng đêm luôn 😉");
        return sb.toString();
    }

    private String buildAvailabilityAnswer(List<RoomType> matchedTypes) {
        List<RoomType> types = matchedTypes.isEmpty() ? roomTypeRepository.findAll() : matchedTypes;
        LocalDate today = LocalDate.now();
        StringBuilder sb = new StringBuilder("Lịch trống " + AVAILABILITY_LOOKAHEAD_DAYS + " ngày tới nè 📅");
        for (RoomType rt : types) {
            List<String> freeDays = new ArrayList<>();
            for (int i = 0; i < AVAILABILITY_LOOKAHEAD_DAYS; i++) {
                LocalDate checkIn = today.plusDays(i);
                int freeCount = roomRepository.findAvailableRooms(checkIn, checkIn.plusDays(1), rt.getId(), null).size();
                if (freeCount > 0) {
                    freeDays.add(checkIn.format(DATE_FMT) + " (" + freeCount + " phòng)");
                }
            }
            sb.append("\n• ").append(rt.getName()).append(": ");
            sb.append(freeDays.isEmpty() ? "kín phòng mất rồi 🥲" : String.join(", ", freeDays));
        }
        sb.append("\nBạn chọn ngày cụ thể ở trang **Danh sách phòng** để Mây kiểm tra chính xác nha!");
        return sb.toString();
    }

    // Lay dung cai dat dat coc hien tai (admin chinh o trang "Gia & dat coc")
    private String buildDepositAnswer() {
        PricingSettings s = settings();
        if (s.getDepositPercent() <= 0) {
            return "Hiện Mây **không yêu cầu đặt cọc** đâu nè 🥳 Bạn chỉ cần đặt phòng online, "
                    + "thanh toán khi trả phòng là được.";
        }
        return "Để giữ phòng, bạn chuyển cọc **" + s.getDepositPercent() + "%** tổng tiền trong vòng **"
                + s.getDepositDeadlineHours() + " giờ** sau khi đặt nha 🔒\n"
                + "Đặt xong sẽ có mã QR chuyển khoản điền sẵn số tiền, Mây xác nhận đơn ngay khi nhận được cọc. "
                + "Phần còn lại bạn thanh toán lúc trả phòng.";
    }

    private String buildFacilitiesAnswer(List<RoomType> matchedTypes) {
        StringBuilder sb = new StringBuilder("Ở Mây có mấy thứ xinh lắm nè ✨\n")
                .append("• 🔥 BBQ ngoài trời\n")
                .append("• ☁️ Ban công ngắm mây\n")
                .append("• 🪵 Đốt lửa trại\n")
                .append("• 🍳 Bếp chung để tự nấu\n")
                .append("• 🛵 Thuê xe máy\n")
                .append("• 📶 Wi-Fi và 🅿️ chỗ đỗ xe miễn phí");
        for (RoomType rt : matchedTypes) {
            if (rt.getAmenities() != null && !rt.getAmenities().isBlank()) {
                sb.append("\nTiện ích riêng của ").append(rt.getName()).append(": ").append(rt.getAmenities());
            }
        }
        return sb.toString();
    }

    private String buildComboAnswer() {
        List<Combo> combos = comboRepository.findByActiveTrue();
        if (combos.isEmpty()) {
            return "Hiện Mây chưa mở combo ăn uống nào 🥲 Nhưng bếp chung luôn sẵn sàng để bạn tự nấu nha!";
        }
        StringBuilder sb = new StringBuilder("Combo ăn uống cho tối se lạnh nè 🍲");
        for (Combo c : combos) {
            sb.append("\n• ").append(c.getName()).append(": **").append(formatVnd(c.getPrice())).append("đ**");
            if (c.getDescription() != null && !c.getDescription().isBlank()) {
                sb.append(" (").append(c.getDescription()).append(")");
            }
        }
        sb.append("\nBạn chọn combo ngay lúc đặt phòng là được nha!");
        return sb.toString();
    }

    // Hoi "nguoi moi/khach moi/lan dau" -> chi tra loi ma ap dung cho CustomerType.NEW hoac ma dung chung
    private String buildPromotionAnswer(String normalizedQuestion) {
        boolean askingForNew = containsAny(normalizedQuestion, "nguoi moi", "khach moi", "lan dau", "moi dang ky", "khach hang moi");
        List<DiscountCode> relevant = discountCodeRepository.findByActiveTrue().stream()
                .filter(c -> !askingForNew || c.getApplicableCustomerType() == null
                        || c.getApplicableCustomerType() == CustomerType.NEW)
                .toList();

        if (relevant.isEmpty()) {
            return askingForNew
                    ? "Hiện chưa có mã riêng cho khách mới 🥲 Bạn ghé trang chủ thường xuyên để săn ưu đãi nha!"
                    : "Hiện Mây chưa có mã giảm giá nào 🥲 Có ưu đãi mới Mây sẽ báo ngay!";
        }

        StringBuilder sb = new StringBuilder(askingForNew ? "Ưu đãi cho khách mới nè 🎁" : "Mã ưu đãi đang có nè 🎁");
        for (DiscountCode c : relevant) {
            sb.append("\n• **").append(c.getCode()).append("**: giảm ");
            if (c.getDiscountType() == DiscountType.PERCENTAGE) {
                sb.append(c.getDiscountValue().stripTrailingZeros().toPlainString()).append("%");
            } else {
                sb.append(formatVnd(c.getDiscountValue())).append("đ");
            }
            if (c.getApplicableCustomerType() != null) {
                sb.append(" (dành cho ").append(c.getApplicableCustomerType().getVietnameseLabel().toLowerCase()).append(")");
            }
            if (c.getDescription() != null && !c.getDescription().isBlank()) {
                sb.append(" – ").append(c.getDescription());
            }
        }
        sb.append("\nNhập mã ở bước xác nhận đặt phòng là được giảm liền nha!");
        return sb.toString();
    }

    private PricingSettings settings() {
        PricingSettings s = pricingService.getSettings();
        return s != null ? s : PricingSettings.defaults();
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

    // Bo dau tieng Viet + ve chu thuong + bo dau cau de so khop tu khoa khong phu thuoc dau/hoa-thuong
    private String normalize(String s) {
        String temp = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        temp = temp.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        temp = temp.replace('đ', 'd').replace('Đ', 'd');
        temp = temp.replaceAll("[^a-z0-9]+", " ").trim();
        return " " + temp + " ";
    }
}
