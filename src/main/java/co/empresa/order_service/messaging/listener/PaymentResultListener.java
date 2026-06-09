package co.empresa.order_service.messaging.listener;

import co.empresa.order_service.config.RabbitMQConfig;
import co.empresa.order_service.messaging.dto.PaymentResultEvent;
import co.empresa.order_service.service.CheckoutService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Escucha los resultados de pago que publica el payment-service
 * y delega en CheckoutService para actualizar el estado del carrito.
 */
@Component
@RequiredArgsConstructor
public class PaymentResultListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultListener.class);

    private final CheckoutService checkoutService;

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_RESULT_QUEUE)
    public void handlePaymentResult(PaymentResultEvent event) {
        log.info("[RabbitMQ] ← PaymentResultEvent — cartId={} status={} paymentId={}",
                event.getCartId(), event.getStatus(), event.getPaymentId());

        try {
            switch (event.getStatus()) {
                case "APPROVED"  -> checkoutService.handlePaymentApproved(event);
                case "REJECTED"  -> checkoutService.handlePaymentRejected(event);
                case "FAILED"    -> checkoutService.handlePaymentFailed(event);
                case "REFUNDED"  -> checkoutService.handlePaymentRefunded(event);
                default -> log.warn("[RabbitMQ] Estado desconocido '{}' para cartId={}",
                        event.getStatus(), event.getCartId());
            }
        } catch (Exception e) {
            log.error("[RabbitMQ] Error procesando resultado de pago para cartId={}: {}",
                    event.getCartId(), e.getMessage());
            // No relanzamos — si RabbitMQ reintenta un mensaje que no pudo procesarse,
            // puede entrar en loop infinito. Loguear y continuar es lo correcto aquí.
        }
    }
}
