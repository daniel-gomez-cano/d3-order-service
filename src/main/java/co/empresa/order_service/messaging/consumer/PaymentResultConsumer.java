package co.empresa.order_service.messaging.consumer;

import co.empresa.order_service.config.RabbitMQConfig;
import co.empresa.order_service.messaging.dto.PaymentResultEvent;
import co.empresa.order_service.service.CheckoutService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

/**
 * Escucha los resultados de pago publicados por el payment-service.
 * Según el estado recibido, actualiza el carrito y dispara acciones:
 *
 *   APPROVED → marcar carrito como CHECKED_OUT, publicar evento al ticket-service
 *   REJECTED → marcar carrito como ACTIVE nuevamente (el cliente puede reintentar)
 *   FAILED   → marcar carrito como ACTIVE, notificar al cliente
 *   REFUNDED → cancelar boletas generadas
 */
@Service
@RequiredArgsConstructor
public class PaymentResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultConsumer.class);

    private final CheckoutService checkoutService;

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_RESULT_QUEUE)
    public void handlePaymentResult(PaymentResultEvent event) {
        log.info("Recibido PaymentResultEvent: cartId={} status={} paymentId={}",
                event.getCartId(), event.getStatus(), event.getPaymentId());

        try {
            switch (event.getStatus()) {
                case "APPROVED" -> checkoutService.handlePaymentApproved(event);
                case "REJECTED" -> checkoutService.handlePaymentRejected(event);
                case "FAILED"   -> checkoutService.handlePaymentFailed(event);
                case "REFUNDED" -> checkoutService.handlePaymentRefunded(event);
                case "PENDING"  -> log.info("Pago PENDING para cartId={}, esperando confirmación",
                        event.getCartId());
                default -> log.warn("Estado de pago desconocido: {} para cartId={}",
                        event.getStatus(), event.getCartId());
            }
        } catch (Exception e) {
            log.error("Error procesando PaymentResultEvent para cartId={}: {}",
                    event.getCartId(), e.getMessage(), e);
            // Re-lanzar para que RabbitMQ reintente (dead letter queue en producción)
            throw e;
        }
    }
}
