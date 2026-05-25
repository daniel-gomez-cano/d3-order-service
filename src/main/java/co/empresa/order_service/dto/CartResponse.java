package co.empresa.order_service.dto;

import co.empresa.order_service.model.Cart.CartStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CartResponse {
    private String id;
    private String buyerId;
    private CartStatus status;
    private LocalDateTime expiresAt;
    private List<CartItemResponse> items;
    private String discountCode;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal total;
    private LocalDateTime createdAt;
}
