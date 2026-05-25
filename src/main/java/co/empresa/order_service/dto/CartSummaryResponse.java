package co.empresa.order_service.dto;

import co.empresa.order_service.model.DiscountCode.DiscountType;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CartSummaryResponse {
    private String cartId;
    private List<CartItemResponse> items;
    private String discountCode;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal total;
    private LocalDateTime expiresAt;
    private long minutesRemaining;
}
