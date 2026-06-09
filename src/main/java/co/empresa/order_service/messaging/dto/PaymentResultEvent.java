package co.empresa.order_service.messaging.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Mensaje recibido desde el payment-service con el resultado de un pago.
 * El CheckoutService reacciona según el status para actualizar el carrito.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentResultEvent {

    private String cartId;
    private String paymentId;
    private String mercadoPagoPaymentId;
    private String buyerId;
    private String status;
    private BigDecimal amount;
    private String statusDetail;
    private LocalDateTime processedAt;
}