package com.hotel.unit;

import com.hotel.entity.*;
import com.hotel.repository.ComboRepository;
import com.hotel.repository.DiscountCodeRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.RoomTypeRepository;
import com.hotel.service.ChatbotService;
import com.hotel.tc.TcSteps;
import com.hotel.tc.TcSuite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@TcSuite(level = "UT", module = "Chatbot tư vấn (ChatbotService)")
class ChatbotServiceParamTest {

    @Mock private RoomTypeRepository roomTypeRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private ComboRepository comboRepository;
    @Mock private DiscountCodeRepository discountCodeRepository;

    @InjectMocks private ChatbotService chatbotService;

    @BeforeEach
    void setUp() {
        RoomType normal = RoomType.builder().id(1L).name("Phòng thường").basePrice(bd(200000)).maxGuests(2).amenities("Wi-Fi, Tivi, tủ lạnh").build();
        RoomType vip = RoomType.builder().id(2L).name("Phòng VIP").basePrice(bd(500000)).maxGuests(2).amenities("Bồn tắm, view sông").build();
        when(roomTypeRepository.findAll()).thenReturn(List.of(normal, vip));
        when(roomRepository.findByRoomTypeId(1L)).thenReturn(List.of(room(200000, normal)));
        when(roomRepository.findByRoomTypeId(2L)).thenReturn(List.of(room(500000, vip), room(600000, vip)));
        when(roomRepository.findAvailableRooms(any(), any(), anyLong(), any())).thenReturn(List.of(room(200000, normal)));
        when(comboRepository.findByActiveTrue()).thenReturn(List.of(
                Combo.builder().name("Lẩu").price(bd(399000)).description("Lẩu thái 2 người").active(true).build(),
                Combo.builder().name("Nướng BBQ").price(bd(299000)).active(true).build()));
        when(discountCodeRepository.findByActiveTrue()).thenReturn(List.of(
                code("WELCOME10", DiscountType.PERCENTAGE, 10, CustomerType.NEW),
                code("VIP20", DiscountType.PERCENTAGE, 20, CustomerType.VIP),
                code("SUMMER50K", DiscountType.FIXED_AMOUNT, 50000, null)));
    }

    private static BigDecimal bd(long v) {
        return BigDecimal.valueOf(v);
    }

    private static Room room(long price, RoomType type) {
        return Room.builder().roomNumber("R").roomType(type).price(bd(price)).status(RoomStatus.AVAILABLE).build();
    }

    private static DiscountCode code(String c, DiscountType t, long v, CustomerType ct) {
        return DiscountCode.builder().code(c).discountType(t).discountValue(bd(v)).applicableCustomerType(ct).active(true).build();
    }

    @TcSteps("Gọi ChatbotService.answer(câu hỏi) với dữ liệu phòng/combo/mã giảm giá giả lập, kiểm tra nội dung trả lời")
    @ParameterizedTest(name = "Chatbot hiểu ý định [{1}] ¦ câu hỏi: \"{0}\" ¦ Câu trả lời chứa \"{2}\"")
    @CsvSource(delimiter = '|', value = {
            "Giá phòng bao nhiêu?|Hỏi giá|Giá phòng hiện tại:",
            "gia phong bao nhieu tien|Hỏi giá (không dấu)|Giá phòng hiện tại:",
            "GIÁ PHÒNG VIP|Hỏi giá theo loại phòng (chữ hoa)|Phòng VIP: 500.000 - 600.000 VND/đêm",
            "phòng thường bao nhiêu 1 đêm|Hỏi giá phòng thường|Phòng thường: 200.000 VND/đêm",
            "Còn phòng trống không?|Hỏi phòng trống|trong 7 ngày tới",
            "con phong khong ban|Hỏi phòng trống (không dấu)|trong 7 ngày tới",
            "Ngày nào trống phòng VIP|Hỏi phòng trống theo loại|Phòng VIP trong 7 ngày tới",
            "Có cần đặt cọc không?|Hỏi đặt cọc|KHÔNG yêu cầu đặt cọc",
            "dat coc bao nhieu|Hỏi đặt cọc (không dấu)|KHÔNG yêu cầu đặt cọc",
            "ĐẶT CỌC TRƯỚC KHÔNG|Hỏi đặt cọc (chữ hoa)|KHÔNG yêu cầu đặt cọc",
            "Thanh toán bằng hình thức nào?|Hỏi thanh toán|Tiền mặt, Chuyển khoản ngân hàng",
            "tra tien the nao|Hỏi thanh toán (không dấu)|Thanh toán online",
            "PHƯƠNG THỨC THANH TOÁN|Hỏi thanh toán (chữ hoa)|Tiền mặt",
            "Khách sạn có hồ bơi không?|Hỏi tiện ích|Hồ bơi",
            "co phong gym khong|Hỏi tiện ích (không dấu)|Phòng gym",
            "Tiện ích phòng VIP có gì|Hỏi tiện ích riêng của loại phòng|Tiện ích riêng của Phòng VIP: Bồn tắm",
            "Có đưa đón sân bay không|Hỏi dịch vụ đưa đón|Đưa đón sân bay",
            "Có combo ăn uống gì?|Hỏi combo|Lẩu: 399.000 VND (Lẩu thái 2 người)",
            "co buffet khong|Hỏi combo (không dấu)|Nướng BBQ: 299.000 VND",
            "ĐỒ ĂN CÓ GÌ|Hỏi combo (chữ hoa)|Các combo dịch vụ hiện có",
            "Có mã giảm giá không?|Hỏi khuyến mãi|Các mã ưu đãi đang áp dụng",
            "khuyen mai gi khong|Hỏi khuyến mãi (không dấu)|VIP20: giảm 20%",
            "Có voucher nào không|Hỏi voucher|SUMMER50K: giảm 50.000 VND",
            "Khách sạn ở đâu?|Hỏi địa chỉ|123 đường Phan Tây Nhạc",
            "dia chi khach san|Hỏi địa chỉ (không dấu)|Từ Liêm, Hà Nội",
            "VỊ TRÍ KHÁCH SẠN|Hỏi vị trí (chữ hoa)|Địa chỉ Khách sạn EAUT",
            "Số điện thoại liên hệ là gì?|Hỏi liên hệ|0338932368",
            "hotline khach san|Hỏi hotline (không dấu)|0338932368",
            "cho xin email|Hỏi email|khachsanEAUT@gmail.com",
            "Mấy giờ nhận phòng?|Hỏi giờ nhận phòng|Giờ nhận phòng tiêu chuẩn: 12h trưa",
            "may gio tra phong|Hỏi giờ trả phòng (không dấu)|Giờ trả phòng tiêu chuẩn: 9h sáng",
            "CHECK IN LÚC NÀO|Hỏi check-in (chữ hoa)|Giờ nhận phòng tiêu chuẩn",
            "Xin chào|Chào hỏi|Xin chào! Mình là trợ lý ảo",
            "hello|Chào hỏi tiếng Anh|trợ lý ảo của Khách sạn EAUT",
            "hi|Chào hỏi ngắn|trợ lý ảo",
            "Phòng VIP|Chỉ nhắc tên loại phòng|Phòng VIP: 500.000 - 600.000 VND/đêm",
            "phong thuong|Chỉ nhắc tên loại phòng (không dấu)|Phòng thường trong 7 ngày tới",
            "abc xyz|Câu hỏi không hiểu|Mình có thể giúp bạn tra cứu",
            "thời tiết hôm nay|Câu hỏi ngoài phạm vi|Phòng VIP giá bao nhiêu?",
            "Giá phòng và địa chỉ ở đâu|Hỏi kết hợp nhiều ý|123 đường Phan Tây Nhạc"})
    void answer_detectsIntent(String question, String intent, String expected) {
        assertThat(chatbotService.answer(question)).contains(expected);
    }

    @TcSteps("Gọi ChatbotService.answer() với câu hỏi rỗng")
    @ParameterizedTest(name = "Câu hỏi rỗng ¦ message=[{0}] ¦ Trả lời gợi ý \"Bạn muốn hỏi gì...\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void answer_blank(String q) {
        assertThat(chatbotService.answer(q)).startsWith("Bạn muốn hỏi gì");
    }

    @TcSteps("Gọi answer() hỏi ưu đãi cho khách mới, dữ liệu có mã NEW, VIP và mã dùng chung")
    @ParameterizedTest(name = "Hỏi ưu đãi cho khách mới - kiểm tra mã {1} ¦ câu hỏi: \"{0}\" ¦ Có chứa {1} = {2}")
    @CsvSource(delimiter = '|', value = {
            "Có ưu đãi gì cho người mới không|WELCOME10|true",
            "Có ưu đãi gì cho người mới không|SUMMER50K|true",
            "Có ưu đãi gì cho người mới không|VIP20|false",
            "khuyen mai cho khach moi lan dau|VIP20|false"})
    void answer_promotionForNewCustomers(String q, String code, boolean present) {
        String reply = chatbotService.answer(q);
        assertThat(reply).contains("Ưu đãi dành cho khách hàng mới");
        assertThat(reply.contains(code)).isEqualTo(present);
    }

    @TcSteps("Giả lập kho dữ liệu trống (không có loại phòng/combo/mã giảm giá), gọi answer()")
    @ParameterizedTest(name = "Dữ liệu trống - hỏi {0} ¦ câu hỏi: \"{1}\" ¦ Trả lời \"{2}\"")
    @CsvSource(delimiter = '|', value = {
            "giá|Giá phòng bao nhiêu|chưa cập nhật loại phòng nào",
            "combo|Có combo gì|chưa có combo dịch vụ ăn uống nào",
            "mã giảm giá|Có mã giảm giá không|chưa có mã giảm giá nào",
            "ưu đãi khách mới|Ưu đãi cho người mới|chưa có mã ưu đãi riêng cho khách hàng mới"})
    void answer_emptyData(String topic, String q, String expected) {
        when(roomTypeRepository.findAll()).thenReturn(List.of());
        when(comboRepository.findByActiveTrue()).thenReturn(List.of());
        when(discountCodeRepository.findByActiveTrue()).thenReturn(List.of());
        assertThat(chatbotService.answer(q)).contains(expected);
    }

    @TcSteps("Giả lập loại phòng VIP không còn phòng trống trong 7 ngày, gọi answer()")
    @ParameterizedTest(name = "Hết phòng trong 7 ngày tới ¦ câu hỏi: \"{0}\" ¦ Trả lời \"hiện không còn phòng trống\"")
    @ValueSource(strings = {"Phòng VIP còn phòng không", "het phong chua"})
    void answer_noAvailability(String q) {
        when(roomRepository.findAvailableRooms(any(), any(), anyLong(), any())).thenReturn(List.of());
        assertThat(chatbotService.answer(q)).contains("hiện không còn phòng trống");
    }
}
