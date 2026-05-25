package co.empresa.order_service.dto;

import lombok.Data;

/**
 * Cuerpo que envía el payment-service al notificar el resultado de un pago.
 *
 * Coincide con el record PaymentResultNotification del payment-service:
 *   { "paymentId": "uuid", "result": "APPROVED" | "REJECTED" | ... }
 */
@Data
public class PaymentResultRequest {
    private String paymentId;
    private String result;
}
