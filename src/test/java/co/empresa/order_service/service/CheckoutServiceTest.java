package co.empresa.order_service.service;

import co.empresa.order_service.config.EventServiceClient;
import co.empresa.order_service.messaging.dto.OrderCreatedEvent;
import co.empresa.order_service.messaging.dto.PaymentResultEvent;
import co.empresa.order_service.messaging.publisher.OrderEventPublisher;
import co.empresa.order_service.model.Cart;
import co.empresa.order_service.model.Cart.CartStatus;
import co.empresa.order_service.model.CartItem;
import co.empresa.order_service.model.DiscountCode;
import co.empresa.order_service.repository.CartRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    @Mock
    private CartRepository cartRepo;

    @Mock
    private OrderEventPublisher publisher;

    @Mock
    private EventServiceClient eventServiceClient;

    @InjectMocks
    private CheckoutService service;

    @Test
    void initiateCheckout_cuandoHayCarritoActivo_publicaEventoYCambiaEstado() {
        Cart carrito = carritoActivo();
        carrito.getItems().add(item(carrito, "tt-1", "VIP", "100", 2, BigDecimal.valueOf(50000)));
        carrito.setDiscountCode(codigoDescuento());

        when(cartRepo.findByBuyerIdAndStatus("buyer-1", CartStatus.ACTIVE))
                .thenReturn(Optional.of(carrito));
        when(cartRepo.save(carrito)).thenReturn(carrito);

        ArgumentCaptor<OrderCreatedEvent> captor = ArgumentCaptor.forClass(OrderCreatedEvent.class);

        service.initiateCheckout("buyer-1", "buyer@example.com");

        verify(cartRepo).save(carrito);
        verify(publisher).publishOrderCreated(captor.capture());

        OrderCreatedEvent event = captor.getValue();
        assertThat(carrito.getStatus()).isEqualTo(CartStatus.CHECKED_OUT);
        assertThat(event.getCartId()).isEqualTo("cart-1");
        assertThat(event.getBuyerId()).isEqualTo("buyer-1");
        assertThat(event.getBuyerEmail()).isEqualTo("buyer@example.com");
        assertThat(event.getDiscountCode()).isEqualTo("PROMO20");
        assertThat(event.getTotal()).isEqualByComparingTo("80000");
        assertThat(event.getItems()).hasSize(1);
    }

    @Test
    void initiateCheckout_cuandoNoHayCarritoActivo_lanzaNotFound() {
        when(cartRepo.findByBuyerIdAndStatus("buyer-1", CartStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.initiateCheckout("buyer-1", "buyer@example.com"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(publisher, never()).publishOrderCreated(any());
    }

    @Test
    void initiateCheckout_cuandoCarritoEstaVacio_lanzaBadRequest() {
        Cart carrito = carritoActivo();
        when(cartRepo.findByBuyerIdAndStatus("buyer-1", CartStatus.ACTIVE))
                .thenReturn(Optional.of(carrito));

        assertThatThrownBy(() -> service.initiateCheckout("buyer-1", "buyer@example.com"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(cartRepo, never()).save(any());
        verify(publisher, never()).publishOrderCreated(any());
    }

    @Test
    void initiateCheckout_cuandoCarritoVencido_lanzaGoneYLoExpira() {
        Cart carrito = carritoVencido();
        when(cartRepo.findByBuyerIdAndStatus("buyer-1", CartStatus.ACTIVE))
                .thenReturn(Optional.of(carrito));

        assertThatThrownBy(() -> service.initiateCheckout("buyer-1", "buyer@example.com"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.GONE);

        assertThat(carrito.getStatus()).isEqualTo(CartStatus.EXPIRED);
        verify(cartRepo).save(carrito);
        verify(publisher, never()).publishOrderCreated(any());
    }

    @Test
    void handlePaymentApproved_reservaStockYomiteItemsInvalidos() {
        Cart carrito = carritoActivo();
        carrito.getItems().add(item(carrito, "1", "VIP", "100", 2, BigDecimal.valueOf(50000)));
        carrito.getItems().add(itemConDatosIncompletos(carrito));
        carrito.getItems().add(item(carrito, "2", "GENERAL", "100", 1, BigDecimal.valueOf(20000)));

        when(cartRepo.findById("cart-1")).thenReturn(Optional.of(carrito));

        PaymentResultEvent event = PaymentResultEvent.builder()
                .cartId("cart-1")
                .paymentId("pay-1")
                .status("approved")
                .build();

        service.handlePaymentApproved(event);

        verify(eventServiceClient).reserveTickets(100L, 1L, 2);
        verify(eventServiceClient).reserveTickets(100L, 2L, 1);
    }

    @Test
    void handlePaymentApproved_cuandoEventServiceLanzaResponseStatus_noPropaga() {
        Cart carrito = carritoActivo();
        carrito.getItems().add(item(carrito, "1", "VIP", "100", 2, BigDecimal.valueOf(50000)));
        when(cartRepo.findById("cart-1")).thenReturn(Optional.of(carrito));
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "sin stock"))
                .when(eventServiceClient).reserveTickets(100L, 1L, 2);

        service.handlePaymentApproved(PaymentResultEvent.builder().cartId("cart-1").build());

        verify(eventServiceClient).reserveTickets(100L, 1L, 2);
    }

    @Test
    void handlePaymentApproved_cuandoEventServiceLanzaErrorTecnico_noPropaga() {
        Cart carrito = carritoActivo();
        carrito.getItems().add(item(carrito, "1", "VIP", "100", 2, BigDecimal.valueOf(50000)));
        when(cartRepo.findById("cart-1")).thenReturn(Optional.of(carrito));
        doThrow(new IllegalStateException("caida"))
                .when(eventServiceClient).reserveTickets(100L, 1L, 2);

        service.handlePaymentApproved(PaymentResultEvent.builder().cartId("cart-1").build());

        verify(eventServiceClient).reserveTickets(100L, 1L, 2);
    }

    @Test
    void handlePaymentApproved_cuandoCarritoNoExiste_noHaceNada() {
        when(cartRepo.findById("cart-inexistente")).thenReturn(Optional.empty());

        service.handlePaymentApproved(PaymentResultEvent.builder().cartId("cart-inexistente").build());

        verify(eventServiceClient, never()).reserveTickets(anyLong(), anyLong(), anyInt());
    }

    @Test
    void handlePaymentRejected_reactivaElCarrito() {
        Cart carrito = carritoCheckout();
        when(cartRepo.findById("cart-1")).thenReturn(Optional.of(carrito));
        when(cartRepo.save(carrito)).thenReturn(carrito);

        service.handlePaymentRejected(PaymentResultEvent.builder().cartId("cart-1").statusDetail("rechazado").build());

        assertThat(carrito.getStatus()).isEqualTo(CartStatus.ACTIVE);
        verify(cartRepo).save(carrito);
    }

    @Test
    void handlePaymentFailed_reactivaElCarrito() {
        Cart carrito = carritoCheckout();
        when(cartRepo.findById("cart-1")).thenReturn(Optional.of(carrito));
        when(cartRepo.save(carrito)).thenReturn(carrito);

        service.handlePaymentFailed(PaymentResultEvent.builder().cartId("cart-1").statusDetail("error tecnico").build());

        assertThat(carrito.getStatus()).isEqualTo(CartStatus.ACTIVE);
        verify(cartRepo).save(carrito);
    }

    @Test
    void handlePaymentRefunded_noLanzaExcepcion() {
        service.handlePaymentRefunded(PaymentResultEvent.builder().cartId("cart-1").build());
    }

    private Cart carritoActivo() {
        return Cart.builder()
                .id("cart-1")
                .buyerId("buyer-1")
                .status(CartStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(60))
                .items(new ArrayList<>())
                .build();
    }

    private Cart carritoCheckout() {
        Cart cart = carritoActivo();
        cart.setStatus(CartStatus.CHECKED_OUT);
        return cart;
    }

    private Cart carritoVencido() {
        Cart cart = carritoActivo();
        cart.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        return cart;
    }

    private CartItem item(Cart cart, String ticketTypeId, String ticketTypeName, String eventId, int quantity, BigDecimal unitPrice) {
        return CartItem.builder()
                .cart(cart)
                .ticketTypeId(ticketTypeId)
                .ticketTypeName(ticketTypeName)
                .eventId(eventId)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .build();
    }

    private CartItem itemConDatosIncompletos(Cart cart) {
        return CartItem.builder()
                .cart(cart)
                .ticketTypeId("tt-incompleto")
                .ticketTypeName("GENERAL")
                .quantity(1)
                .unitPrice(BigDecimal.valueOf(20000))
                .build();
    }

    private DiscountCode codigoDescuento() {
        return DiscountCode.builder()
                .code("PROMO20")
                .type(DiscountCode.DiscountType.PERCENTAGE)
                .value(BigDecimal.valueOf(20))
                .active(true)
                .usedCount(0)
                .organizerId("org-1")
                .build();
    }
}