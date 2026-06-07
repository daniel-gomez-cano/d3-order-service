package co.empresa.order_service.messaging.publisher;

import co.empresa.order_service.config.RabbitMQConfig;
import co.empresa.order_service.messaging.dto.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Publica eventos del carrito en RabbitMQ.
 * El payment-service escucha estos eventos para iniciar el cobro.
 */
@Service
@RequiredArgsConstructor
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    /**
     * Publica el evento de checkout en order.exchange → order.created.queue.
     * El payment-service lo consume y crea la preferencia en MercadoPago.
     */
    public void publishOrderCreated(OrderCreatedEvent event) {
        log.info("Publicando OrderCreatedEvent para cartId={} buyerId={}",
                event.getCartId(), event.getBuyerId());

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.ORDER_EXCHANGE,
                RabbitMQConfig.ORDER_CREATED_KEY,
                event
        );

        log.info("OrderCreatedEvent publicado exitosamente para cartId={}", event.getCartId());
    }
}
