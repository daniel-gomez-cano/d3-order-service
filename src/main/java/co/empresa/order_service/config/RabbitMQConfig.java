package co.empresa.order_service.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de RabbitMQ para el order-service.
 *
 * Exchanges y colas:
 *
 *   order.exchange  (direct)
 *     └── order.created.queue  ← order-service publica, payment-service consume
 *
 *   payment.exchange (direct)
 *     └── payment.result.queue ← payment-service publica, order-service consume
 */
@Configuration
public class RabbitMQConfig {

    // ── Nombres de exchanges ───────────────────────────────────────────────────
    public static final String ORDER_EXCHANGE    = "order.exchange";
    public static final String PAYMENT_EXCHANGE  = "payment.exchange";

    // ── Nombres de colas ──────────────────────────────────────────────────────
    public static final String ORDER_CREATED_QUEUE  = "order.created.queue";
    public static final String PAYMENT_RESULT_QUEUE = "payment.result.order.queue"; // antes: "payment.result.queue"

    // ── Routing keys ──────────────────────────────────────────────────────────
    public static final String ORDER_CREATED_KEY  = "order.created";
    public static final String PAYMENT_RESULT_KEY = "payment.result";

    // ── Exchanges ─────────────────────────────────────────────────────────────

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(ORDER_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange paymentExchange() {
        return new DirectExchange(PAYMENT_EXCHANGE, true, false);
    }

    // ── Colas ─────────────────────────────────────────────────────────────────

    @Bean
    public Queue orderCreatedQueue() {
        return QueueBuilder.durable(ORDER_CREATED_QUEUE).build();
    }

    @Bean
    public Queue paymentResultQueue() {
        return QueueBuilder.durable(PAYMENT_RESULT_QUEUE).build();
    }

    // ── Bindings ──────────────────────────────────────────────────────────────

    @Bean
    public Binding orderCreatedBinding() {
        return BindingBuilder
                .bind(orderCreatedQueue())
                .to(orderExchange())
                .with(ORDER_CREATED_KEY);
    }

    @Bean
    public Binding paymentResultBinding() {
        return BindingBuilder
                .bind(paymentResultQueue())
                .to(paymentExchange())
                .with(PAYMENT_RESULT_KEY);
    }

    // ── Conversor JSON ────────────────────────────────────────────────────────
    // Serializa/deserializa los mensajes como JSON automáticamente

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
