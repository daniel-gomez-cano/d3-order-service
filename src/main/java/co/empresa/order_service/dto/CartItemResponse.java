package co.empresa.order_service.dto;

import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CartItemResponse {
    private String id;
    private String ticketTypeId;
    private String ticketTypeName;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
}
