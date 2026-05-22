package co.empresa.order_service.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import co.empresa.order_service.config.TicketServiceClient;
import co.empresa.order_service.config.TicketServiceClient.TicketTypeInfo;
import co.empresa.order_service.dto.AddItemRequest;
import co.empresa.order_service.dto.ApplyDiscountRequest;
import co.empresa.order_service.dto.CartItemResponse;
import co.empresa.order_service.dto.CartResponse;
import co.empresa.order_service.dto.CartSummaryResponse;
import co.empresa.order_service.dto.UpdateItemRequest;
import co.empresa.order_service.model.Cart;
import co.empresa.order_service.model.Cart.CartStatus;
import co.empresa.order_service.model.CartItem;
import co.empresa.order_service.model.DiscountCode;
import co.empresa.order_service.repository.CartRepository;
import co.empresa.order_service.repository.DiscountCodeRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepo;
    private final DiscountCodeRepository discountRepo;
    private final TicketServiceClient ticketClient;

    //  SCRUM-42: Obtener o crear carrito activo 

    @Transactional
    public CartResponse getOrCreateCart(String buyerId) {
        Cart cart = cartRepo.findByBuyerIdAndStatus(buyerId, CartStatus.ACTIVE)
                .filter(c -> !c.isExpired())
                .orElseGet(() -> {
                    Cart nuevo = Cart.builder()
                            .buyerId(buyerId)
                            .status(CartStatus.ACTIVE)
                            .build();
                    return cartRepo.save(nuevo);
                });

        return toResponse(cart);
    }

    //  SCRUM-43: Agregar ítem al carrito 

    @Transactional
    public CartResponse addItem(String buyerId, AddItemRequest req) {
        Cart cart = getActiveCart(buyerId);

        // Verificar disponibilidad y obtener precio actual del ticket-service
        TicketTypeInfo info = ticketClient.getTicketTypeInfo(req.getTicketTypeId());

        // Verificar que hay suficientes cupos para la cantidad pedida
        if (info.remainingCapacity() < req.getQuantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo hay " + info.remainingCapacity() + " cupos disponibles");
        }

        // Si ya existe ese tipo en el carrito, actualizar cantidad
        cart.getItems().stream()
                .filter(i -> i.getTicketTypeId().equals(req.getTicketTypeId()))
                .findFirst()
                .ifPresentOrElse(
                        existing -> existing.setQuantity(existing.getQuantity() + req.getQuantity()),
                        () -> {
                            CartItem item = CartItem.builder()
                                    .cart(cart)
                                    .ticketTypeId(info.id())
                                    .ticketTypeName(info.name())
                                    .quantity(req.getQuantity())
                                    .unitPrice(info.price())
                                    .build();
                            cart.getItems().add(item);
                        }
                );

        return toResponse(cartRepo.save(cart));
    }

    //SCRUM-44: Actualizar cantidad de un ítem 

    @Transactional
    public CartResponse updateItem(String buyerId, String itemId, UpdateItemRequest req) {
        Cart cart = getActiveCart(buyerId);

        CartItem item = cart.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Ítem no encontrado en tu carrito"));

        // Verificar disponibilidad con la nueva cantidad
        TicketTypeInfo info = ticketClient.getTicketTypeInfo(item.getTicketTypeId());
        if (info.remainingCapacity() < req.getQuantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo hay " + info.remainingCapacity() + " cupos disponibles");
        }

        item.setQuantity(req.getQuantity());
        return toResponse(cartRepo.save(cart));
    }

    //  SCRUM-45: Eliminar ítem del carrito 

    @Transactional
    public CartResponse removeItem(String buyerId, String itemId) {
        Cart cart = getActiveCart(buyerId);

        boolean removed = cart.getItems().removeIf(i -> i.getId().equals(itemId));
        if (!removed) throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Ítem no encontrado en tu carrito");

        return toResponse(cartRepo.save(cart));
    }

    //  SCRUM-47: Aplicar código de descuento 

    @Transactional
    public CartResponse applyDiscount(String buyerId, ApplyDiscountRequest req) {
        Cart cart = getActiveCart(buyerId);

        DiscountCode code = discountRepo.findByCode(req.getCode().toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Código de descuento no encontrado"));

        if (!code.isValid()) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Este código de descuento no es válido o ya expiró");

        cart.setDiscountCode(code);
        return toResponse(cartRepo.save(cart));
    }

    // SCRUM-48: Resumen del carrito 

    public CartSummaryResponse getSummary(String buyerId) {
        Cart cart = getActiveCart(buyerId);

        BigDecimal subtotal = cart.getItems().stream()
                .map(CartItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal total = cart.getTotal();
        BigDecimal discountAmount = subtotal.subtract(total);
        long minutesRemaining = ChronoUnit.MINUTES.between(LocalDateTime.now(), cart.getExpiresAt());

        return CartSummaryResponse.builder()
                .cartId(cart.getId())
                .items(cart.getItems().stream().map(this::toItemResponse).toList())
                .discountCode(cart.getDiscountCode() != null ? cart.getDiscountCode().getCode() : null)
                .discountType(cart.getDiscountCode() != null ? cart.getDiscountCode().getType() : null)
                .discountValue(cart.getDiscountCode() != null ? cart.getDiscountCode().getValue() : null)
                .subtotal(subtotal)
                .discountAmount(discountAmount)
                .total(total)
                .expiresAt(cart.getExpiresAt())
                .minutesRemaining(Math.max(0, minutesRemaining))
                .build();
    }

    //Job de expiración automática — corre cada 5 minutos

    @Scheduled(fixedRate = 300_000) // 5 minutos en ms
    @Transactional
    public void expireOldCarts() {
        List<Cart> expired = cartRepo.findByStatusAndExpiresAtBefore(
                CartStatus.ACTIVE, LocalDateTime.now());

        for (Cart cart : expired) {
            cart.setStatus(CartStatus.EXPIRED);
            cartRepo.save(cart);
            // Aquí se podría publicar un evento para liberar cupos en ticket-service
            // Por ahora el ticket-service maneja su propio stock al momento de compra
        }

        if (!expired.isEmpty()) {
            System.out.println("[CartService] Expirados " + expired.size() + " carritos");
        }
    }

    

    private Cart getActiveCart(String buyerId) {
        Cart cart = cartRepo.findByBuyerIdAndStatus(buyerId, CartStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No tienes un carrito activo. Crea uno primero."));

        if (cart.isExpired()) {
            cart.setStatus(CartStatus.EXPIRED);
            cartRepo.save(cart);
            throw new ResponseStatusException(HttpStatus.GONE,
                    "Tu carrito expiró. Por favor crea uno nuevo.");
        }
        return cart;
    }

    private CartResponse toResponse(Cart cart) {
        BigDecimal subtotal = cart.getItems().stream()
                .map(CartItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = cart.getTotal();

        return CartResponse.builder()
                .id(cart.getId())
                .buyerId(cart.getBuyerId())
                .status(cart.getStatus())
                .expiresAt(cart.getExpiresAt())
                .items(cart.getItems().stream().map(this::toItemResponse).toList())
                .discountCode(cart.getDiscountCode() != null ? cart.getDiscountCode().getCode() : null)
                .subtotal(subtotal)
                .discountAmount(subtotal.subtract(total))
                .total(total)
                .createdAt(cart.getCreatedAt())
                .build();
    }

    private CartItemResponse toItemResponse(CartItem item) {
        return CartItemResponse.builder()
                .id(item.getId())
                .ticketTypeId(item.getTicketTypeId())
                .ticketTypeName(item.getTicketTypeName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getSubtotal())
                .build();
    }
}
