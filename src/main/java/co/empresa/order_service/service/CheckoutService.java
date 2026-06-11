package co.empresa.order_service.service;

import co.empresa.order_service.config.EventServiceClient;
import co.empresa.order_service.messaging.dto.OrderCreatedEvent;
import co.empresa.order_service.messaging.dto.OrderCreatedEvent.OrderItem;
import co.empresa.order_service.messaging.dto.PaymentResultEvent;
import co.empresa.order_service.messaging.publisher.OrderEventPublisher;
import co.empresa.order_service.model.Cart;
import co.empresa.order_service.model.Cart.CartStatus;
import co.empresa.order_service.model.CartItem;
import co.empresa.order_service.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Gestiona el proceso de checkout:
 * 1. El cliente confirma el carrito → se publica OrderCreatedEvent a RabbitMQ
 * 2. El payment-service procesa el pago y publica PaymentResultEvent
 * 3. Este servicio reacciona al resultado actualizando el carrito
 */
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final CartRepository cartRepo;
    private final OrderEventPublisher publisher;
    private final EventServiceClient eventServiceClient;

    // ── Paso 1: El cliente hace checkout ──────────────────────────────────────

    /**
     * Inicia el proceso de pago publicando el carrito a RabbitMQ.
     * El carrito pasa a CHECKED_OUT temporalmente hasta confirmar el resultado.
     *
     * @param buyerId  sub de Keycloak del comprador
     * @param buyerEmail email del comprador (necesario para MercadoPago)
     */
    @Transactional
    public void initiateCheckout(String buyerId, String buyerEmail) {
        Cart cart = cartRepo.findByBuyerIdAndStatus(buyerId, CartStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No tienes un carrito activo para hacer checkout"));

        if (cart.isExpired()) {
            cart.setStatus(CartStatus.EXPIRED);
            cartRepo.save(cart);
            throw new ResponseStatusException(HttpStatus.GONE,
                    "Tu carrito expiró. Por favor crea uno nuevo.");
        }

        if (cart.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El carrito está vacío");
        }

        // Marcar como CHECKED_OUT — el cliente no puede modificarlo mientras se paga
        cart.setStatus(CartStatus.CHECKED_OUT);
        cartRepo.save(cart);

        // Construir y publicar el evento
        List<OrderItem> items = cart.getItems().stream()
                .map(i -> OrderItem.builder()
                        .ticketTypeId(i.getTicketTypeId())
                        .ticketTypeName(i.getTicketTypeName())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .subtotal(i.getSubtotal())
                        .build())
                .toList();

        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .cartId(cart.getId())
                .buyerId(buyerId)
                .buyerEmail(buyerEmail)
                .items(items)
                .total(cart.getTotal())
                .discountCode(cart.getDiscountCode() != null
                        ? cart.getDiscountCode().getCode() : null)
                .build();

        publisher.publishOrderCreated(event);
        log.info("Checkout iniciado para cartId={} total={}", cart.getId(), cart.getTotal());
    }

    // ── Paso 2: Reaccionar al resultado del pago ──────────────────────────────

    /**
     * Pago aprobado — el carrito queda en CHECKED_OUT (ya completado).
     * El ticket-service generará las boletas cuando reciba su propio evento.
     */
    @Transactional
    public void handlePaymentApproved(PaymentResultEvent event) {
        log.info("Pago APPROVED para cartId={} paymentId={}",
                event.getCartId(), event.getPaymentId());
        Cart cart = cartRepo.findById(event.getCartId()).orElse(null);
        if (cart == null) {
            log.error("[Stock] Carrito no encontrado para cartId={}", event.getCartId());
            return;
        }

        for (CartItem item : cart.getItems()) {

            if (item.getEventId() == null || item.getTicketTypeId() == null) {
                log.warn("[Stock] CartItem sin eventId o ticketTypeId — itemId={}, skipping",
                        item.getId());
                continue;
            }

            try {
                Long eventId      = Long.parseLong(item.getEventId());
                Long ticketTypeId = Long.parseLong(item.getTicketTypeId());

                eventServiceClient.reserveTickets(eventId, ticketTypeId, item.getQuantity());

            } catch (ResponseStatusException e) {
                // 409 = sin stock — inconsistencia grave (pago cobrado sin stock)
                log.error("[Stock] Conflicto de stock — eventId={} ticketTypeId={}: {}",
                        item.getEventId(), item.getTicketTypeId(), e.getReason());
            } catch (Exception e) {
                // Error de red — loguear, nunca revertir un pago ya cobrado
                log.error("[Stock] Error llamando a event-service — eventId={} ticketTypeId={}: {}",
                        item.getEventId(), item.getTicketTypeId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Pago rechazado — devolver el carrito a ACTIVE para que el cliente reintente.
     */
    @Transactional
    public void handlePaymentRejected(PaymentResultEvent event) {
        log.warn("Pago REJECTED para cartId={}: {}", event.getCartId(), event.getStatusDetail());
        reactivateCart(event.getCartId());
    }

    /**
     * Error técnico — devolver el carrito a ACTIVE para que el cliente reintente.
     */
    @Transactional
    public void handlePaymentFailed(PaymentResultEvent event) {
        log.error("Pago FAILED para cartId={}: {}", event.getCartId(), event.getStatusDetail());
        reactivateCart(event.getCartId());
    }

    /**
     * Reembolso procesado — el carrito queda en CHECKED_OUT.
     * Las boletas son canceladas por el ticket-service.
     */
    @Transactional
    public void handlePaymentRefunded(PaymentResultEvent event) {
        log.info("Pago REFUNDED para cartId={}", event.getCartId());
        // El ticket-service cancela las boletas por su propio canal.
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private void reactivateCart(String cartId) {
        cartRepo.findById(cartId).ifPresent(cart -> {
            cart.setStatus(CartStatus.ACTIVE);
            cartRepo.save(cart);
            log.info("Carrito {} reactivado para reintento de pago", cartId);
        });
    }
}
