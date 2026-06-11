package co.empresa.order_service.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.GONE;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import org.springframework.web.server.ResponseStatusException;

import co.empresa.order_service.config.EventServiceClient;
import co.empresa.order_service.config.EventServiceClient.TicketTypeInfo;
import co.empresa.order_service.dto.AddItemRequest;
import co.empresa.order_service.dto.ApplyDiscountRequest;
import co.empresa.order_service.dto.CartResponse;
import co.empresa.order_service.model.Cart;
import co.empresa.order_service.model.Cart.CartStatus;
import co.empresa.order_service.model.CartItem;
import co.empresa.order_service.model.DiscountCode;
import co.empresa.order_service.repository.CartRepository;
import co.empresa.order_service.repository.DiscountCodeRepository;

/**
 * Tests unitarios de CartService.
 *
 * Se mockean las tres dependencias (CartRepository, DiscountCodeRepository
 * y TicketServiceClient) para que los tests no requieran ni base de datos
 * ni comunicación con otros microservicios.
 *
 * Nota: como @PrePersist no se ejecuta fuera de JPA, el helper carritoActivo()
 * configura manualmente los campos que normalmente llena Hibernate.
 */
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepo;

    @Mock
    private DiscountCodeRepository discountRepo;

    @Mock
    private EventServiceClient eventClient;

    @InjectMocks
    private CartService service;

    // ================================================================
    //  getOrCreateCart()
    // ================================================================

    @Test
    void getOrCreateCart_cuandoNoHayCarritoActivo_creaUnoNuevo() {
        when(cartRepo.findByBuyerIdAndStatus("comprador-1", CartStatus.ACTIVE))
                .thenReturn(Optional.empty());

        Cart guardado = carritoActivo("comprador-1");
        when(cartRepo.save(any(Cart.class))).thenReturn(guardado);

        CartResponse result = service.getOrCreateCart("comprador-1");

        assertThat(result.getBuyerId()).isEqualTo("comprador-1");
        assertThat(result.getStatus()).isEqualTo(CartStatus.ACTIVE);
        verify(cartRepo, times(1)).save(any(Cart.class));
    }

    @Test
    void getOrCreateCart_cuandoYaExisteCarritoActivo_retornaElExistente() {
        Cart existente = carritoActivo("comprador-1");
        when(cartRepo.findByBuyerIdAndStatus("comprador-1", CartStatus.ACTIVE))
                .thenReturn(Optional.of(existente));

        CartResponse result = service.getOrCreateCart("comprador-1");

        assertThat(result.getBuyerId()).isEqualTo("comprador-1");
        // No debe persistir un nuevo carrito
        verify(cartRepo, never()).save(any());
    }

    @Test
    void getOrCreateCart_cuandoCarritoExistenteEstaVencido_creaUnoNuevo() {
        Cart vencido = carritoVencido("comprador-1");
        when(cartRepo.findByBuyerIdAndStatus("comprador-1", CartStatus.ACTIVE))
                .thenReturn(Optional.of(vencido));

        Cart nuevo = carritoActivo("comprador-1");
        when(cartRepo.save(any(Cart.class))).thenReturn(nuevo);

        CartResponse result = service.getOrCreateCart("comprador-1");

        assertThat(result.getBuyerId()).isEqualTo("comprador-1");
        // El vencido fue descartado por el filter; se crea uno nuevo
        verify(cartRepo, times(1)).save(any(Cart.class));
    }

    // ================================================================
    //  addItem()
    // ================================================================

    @Test
    void addItem_cuandoHayCapacidad_agregaItemAlCarrito() {
        Cart carrito = carritoActivo("comprador-1");
        when(cartRepo.findByBuyerIdAndStatus("comprador-1", CartStatus.ACTIVE))
                .thenReturn(Optional.of(carrito));

        TicketTypeInfo info = new TicketTypeInfo("tt-1", "VIP", BigDecimal.valueOf(50_000), 10, true, 10L);
        when(eventClient.getTicketTypeInfo("tt-1")).thenReturn(info);
        when(cartRepo.save(carrito)).thenReturn(carrito);

        AddItemRequest req = new AddItemRequest();
        req.setTicketTypeId("tt-1");
        req.setQuantity(2);

        CartResponse result = service.addItem("comprador-1", req);

        assertThat(result).isNotNull();
        assertThat(carrito.getItems()).hasSize(1);
        assertThat(carrito.getItems().get(0).getQuantity()).isEqualTo(2);
        verify(cartRepo).save(carrito);
    }

    @Test
    void addItem_cuandoNoHaySuficienteCapacidad_lanzaConflict() {
        Cart carrito = carritoActivo("comprador-1");
        when(cartRepo.findByBuyerIdAndStatus("comprador-1", CartStatus.ACTIVE))
                .thenReturn(Optional.of(carrito));

        // Solo queda 1 cupo pero se piden 5
        TicketTypeInfo info = new TicketTypeInfo("tt-1", "VIP", BigDecimal.valueOf(50_000), 1, true, 10L);
        when(eventClient.getTicketTypeInfo("tt-1")).thenReturn(info);

        AddItemRequest req = new AddItemRequest();
        req.setTicketTypeId("tt-1");
        req.setQuantity(5);

        assertThatThrownBy(() -> service.addItem("comprador-1", req))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(CONFLICT);

        verify(cartRepo, never()).save(any());
    }

    @Test
    void addItem_cuandoItemYaExiste_incrementaCantidad() {
        Cart carrito = carritoActivo("comprador-1");
        CartItem existente = itemEnCarrito(carrito, "tt-1", 1);
        carrito.getItems().add(existente);

        when(cartRepo.findByBuyerIdAndStatus("comprador-1", CartStatus.ACTIVE))
                .thenReturn(Optional.of(carrito));

        TicketTypeInfo info = new TicketTypeInfo("tt-1", "VIP", BigDecimal.valueOf(50_000), 10, true, 10L);
        when(eventClient.getTicketTypeInfo("tt-1")).thenReturn(info);
        when(cartRepo.save(carrito)).thenReturn(carrito);

        AddItemRequest req = new AddItemRequest();
        req.setTicketTypeId("tt-1");
        req.setQuantity(3);

        service.addItem("comprador-1", req);

        // Debe seguir siendo 1 solo item, pero con cantidad 4 (1 + 3)
        assertThat(carrito.getItems()).hasSize(1);
        assertThat(carrito.getItems().get(0).getQuantity()).isEqualTo(4);
    }

    // ================================================================
    //  removeItem()
    // ================================================================

    @Test
    void removeItem_cuandoItemExiste_loElimina() {
        Cart carrito = carritoActivo("comprador-1");
        CartItem item = itemEnCarrito(carrito, "tt-1", 2);
        carrito.getItems().add(item);

        when(cartRepo.findByBuyerIdAndStatus("comprador-1", CartStatus.ACTIVE))
                .thenReturn(Optional.of(carrito));
        when(cartRepo.save(carrito)).thenReturn(carrito);

        service.removeItem("comprador-1", "item-1");

        assertThat(carrito.getItems()).isEmpty();
        verify(cartRepo).save(carrito);
    }

    @Test
    void removeItem_cuandoItemNoExiste_lanzaNotFound() {
        Cart carrito = carritoActivo("comprador-1");
        when(cartRepo.findByBuyerIdAndStatus("comprador-1", CartStatus.ACTIVE))
                .thenReturn(Optional.of(carrito));

        assertThatThrownBy(() -> service.removeItem("comprador-1", "id-inexistente"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(NOT_FOUND);
    }

    @Test
    void removeItem_cuandoNoHayCarritoActivo_lanzaNotFound() {
        when(cartRepo.findByBuyerIdAndStatus("comprador-sin-carrito", CartStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeItem("comprador-sin-carrito", "item-1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(NOT_FOUND);
    }

    // ================================================================
    //  applyDiscount()
    // ================================================================

    @Test
    void applyDiscount_cuandoCodigoValido_loAplicaAlCarrito() {
        Cart carrito = carritoActivo("comprador-1");
        when(cartRepo.findByBuyerIdAndStatus("comprador-1", CartStatus.ACTIVE))
                .thenReturn(Optional.of(carrito));

        DiscountCode codigoValido = codigoDescuento("PROMO20", true, null, 0, null);
        when(discountRepo.findByCode("PROMO20")).thenReturn(Optional.of(codigoValido));
        when(cartRepo.save(carrito)).thenReturn(carrito);

        ApplyDiscountRequest req = new ApplyDiscountRequest();
        req.setCode("PROMO20");

        service.applyDiscount("comprador-1", req);

        assertThat(carrito.getDiscountCode()).isEqualTo(codigoValido);
        verify(cartRepo).save(carrito);
    }

    @Test
    void applyDiscount_cuandoCodigoNoExiste_lanzaNotFound() {
        Cart carrito = carritoActivo("comprador-1");
        when(cartRepo.findByBuyerIdAndStatus("comprador-1", CartStatus.ACTIVE))
                .thenReturn(Optional.of(carrito));
        when(discountRepo.findByCode("NOEXISTE")).thenReturn(Optional.empty());

        ApplyDiscountRequest req = new ApplyDiscountRequest();
        req.setCode("NOEXISTE");

        assertThatThrownBy(() -> service.applyDiscount("comprador-1", req))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(NOT_FOUND);
    }

    @Test
    void applyDiscount_cuandoCodigoVencido_lanzaConflict() {
        Cart carrito = carritoActivo("comprador-1");
        when(cartRepo.findByBuyerIdAndStatus("comprador-1", CartStatus.ACTIVE))
                .thenReturn(Optional.of(carrito));

        DiscountCode vencido = codigoDescuento(
                "VENCIDO", true,
                LocalDateTime.now().minusDays(1),   // ya expiró
                0, null);
        when(discountRepo.findByCode("VENCIDO")).thenReturn(Optional.of(vencido));

        ApplyDiscountRequest req = new ApplyDiscountRequest();
        req.setCode("VENCIDO");

        assertThatThrownBy(() -> service.applyDiscount("comprador-1", req))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(CONFLICT);
    }

    @Test
    void applyDiscount_cuandoCodigoInactivo_lanzaConflict() {
        Cart carrito = carritoActivo("comprador-1");
        when(cartRepo.findByBuyerIdAndStatus("comprador-1", CartStatus.ACTIVE))
                .thenReturn(Optional.of(carrito));

        DiscountCode inactivo = codigoDescuento("INACTIVO", false, null, 0, null);
        when(discountRepo.findByCode("INACTIVO")).thenReturn(Optional.of(inactivo));

        ApplyDiscountRequest req = new ApplyDiscountRequest();
        req.setCode("INACTIVO");

        assertThatThrownBy(() -> service.applyDiscount("comprador-1", req))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(CONFLICT);
    }

    // ================================================================
    //  Endpoints internos — markPaid() y markPaymentFailed()
    // ================================================================

    @Test
    void markPaid_cuandoCarritoActivo_marcaComoPagado() {
        Cart carrito = carritoActivo("comprador-1");
        when(cartRepo.findById("carrito-1")).thenReturn(Optional.of(carrito));
        when(cartRepo.save(carrito)).thenReturn(carrito);

        service.markPaid("carrito-1", "pago-xyz");

        assertThat(carrito.getStatus()).isEqualTo(CartStatus.PAID);
        verify(cartRepo).save(carrito);
    }

    @Test
    void markPaid_cuandoYaEstaPagado_noGuardaNuevamente() {
        // Idempotente: llamarlo dos veces no rompe nada
        Cart carrito = carritoActivo("comprador-1");
        carrito.setStatus(CartStatus.PAID);
        when(cartRepo.findById("carrito-1")).thenReturn(Optional.of(carrito));

        service.markPaid("carrito-1", "pago-xyz");

        verify(cartRepo, never()).save(any());
    }

    @Test
    void markPaymentFailed_reviertaCarritoAActivoConTiempoExtra() {
        Cart carrito = carritoActivo("comprador-1");
        carrito.setStatus(CartStatus.CHECKED_OUT);
        when(cartRepo.findById("carrito-1")).thenReturn(Optional.of(carrito));
        when(cartRepo.save(carrito)).thenReturn(carrito);

        service.markPaymentFailed("carrito-1", "pago-xyz", "fondos insuficientes");

        assertThat(carrito.getStatus()).isEqualTo(CartStatus.ACTIVE);
        // Se le deben haber sumado ~30 minutos extras
        assertThat(carrito.getExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(25));
        verify(cartRepo).save(carrito);
    }

    @Test
    void markPaymentFailed_cuandoYaEstaPagado_noRevertePago() {
        // Un pago ya confirmado no se puede revertir aunque llegue un webhook de fallo
        Cart carrito = carritoActivo("comprador-1");
        carrito.setStatus(CartStatus.PAID);
        when(cartRepo.findById("carrito-1")).thenReturn(Optional.of(carrito));

        service.markPaymentFailed("carrito-1", "pago-xyz", "error tardio");

        assertThat(carrito.getStatus()).isEqualTo(CartStatus.PAID);
        verify(cartRepo, never()).save(any());
    }

    @Test
    void markCheckedOut_cuandoCarritoActivo_cambiaEstado() {
        Cart carrito = carritoActivo("comprador-1");
        when(cartRepo.findById("carrito-1")).thenReturn(Optional.of(carrito));
        when(cartRepo.save(carrito)).thenReturn(carrito);

        service.markCheckedOut("carrito-1");

        assertThat(carrito.getStatus()).isEqualTo(CartStatus.CHECKED_OUT);
        verify(cartRepo).save(carrito);
    }

    @Test
    void markCheckedOut_cuandoCarritoVencido_lanzaGone() {
        Cart carrito = carritoVencido("comprador-1");
        carrito.setStatus(CartStatus.EXPIRED);
        when(cartRepo.findById("carrito-1")).thenReturn(Optional.of(carrito));

        assertThatThrownBy(() -> service.markCheckedOut("carrito-1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(GONE);
    }

    // ================================================================
    //  Helpers
    // ================================================================

    /**
     * Crea un carrito en estado ACTIVE que NO está vencido.
     * Configura manualmente los campos que normalmente llena @PrePersist.
     */
    private Cart carritoActivo(String compradorId) {
        return Cart.builder()
                .id("carrito-1")
                .buyerId(compradorId)
                .status(CartStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(60))
                .items(new ArrayList<>())
                .build();
    }

    /** Crea un carrito cuya expiresAt ya pasó (simula un carrito caducado). */
    private Cart carritoVencido(String compradorId) {
        return Cart.builder()
                .id("carrito-vencido")
                .buyerId(compradorId)
                .status(CartStatus.ACTIVE)
                .createdAt(LocalDateTime.now().minusHours(2))
                .expiresAt(LocalDateTime.now().minusMinutes(10))
                .items(new ArrayList<>())
                .build();
    }

    /** Crea un CartItem asociado al carrito dado, con id "item-1". */
    private CartItem itemEnCarrito(Cart carrito, String ticketTypeId, int cantidad) {
        return CartItem.builder()
                .id("item-1")
                .cart(carrito)
                .ticketTypeId(ticketTypeId)
                .ticketTypeName("VIP")
                .quantity(cantidad)
                .unitPrice(BigDecimal.valueOf(50_000))
                .build();
    }

    /** Construye un DiscountCode configurable para distintos escenarios. */
    private DiscountCode codigoDescuento(
            String codigo,
            boolean activo,
            LocalDateTime expiresAt,
            int usosRealizados,
            Integer maxUsos) {

        DiscountCode dc = DiscountCode.builder()
                .code(codigo)
                .type(DiscountCode.DiscountType.PERCENTAGE)
                .value(BigDecimal.valueOf(20))
                .active(activo)
                .usedCount(usosRealizados)
                .expiresAt(expiresAt)
                .maxUses(maxUsos)
                .organizerId("org-1")
                .build();
        dc.setId("dc-1");
        return dc;
    }
}
