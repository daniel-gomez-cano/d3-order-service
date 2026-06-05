package co.empresa.order_service.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Respuesta del endpoint interno GET /internal/carts/{cartId}/summary.
 *
 * Los nombres de campos DEBEN coincidir exactamente con el record
 * CartSummaryInternal del payment-service (Jackson los deserializa por nombre).
 *
 *   cartId   → cartId
 *   buyerId  → buyerId
 *   total    → total
 *   currency → currency
 *   items    → items (lista de ItemSummary)
 */
@Data
@Builder
public class InternalCartSummaryResponse {

    private String cartId;
    private String buyerId;
    private BigDecimal total;
    private String currency;
    private List<ItemSummary> items;

    @Data
    @Builder
    public static class ItemSummary {
        private String ticketTypeName;
        private int quantity;
        private BigDecimal unitPrice;
    }
}
