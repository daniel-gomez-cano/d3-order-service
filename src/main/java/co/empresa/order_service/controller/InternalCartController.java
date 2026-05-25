package co.empresa.order_service.controller;

import co.empresa.order_service.dto.InternalCartSummaryResponse;
import co.empresa.order_service.dto.PaymentResultRequest;
import co.empresa.order_service.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints internos del order-service — solo para comunicación con el payment-service.
 *
 * Base path: /internal/carts
 *
 * IMPORTANTE: Estos endpoints NO requieren JWT de Keycloak porque el payment-service
 * los llama desde el webhook de MercadoPago, donde ya no hay token del usuario.
 * En producción real esto estaría protegido por red privada (Docker network) o mTLS.
 * Para el proyecto académico, la seguridad la da el hecho de que el endpoint
 * solo es accesible dentro de la red de contenedores Docker.
 *
 * Endpoints:
 *   GET  /internal/carts/{cartId}/summary        → resumen para crear preferencia en MP
 *   POST /internal/carts/{cartId}/checkout       → marcar carrito CHECKED_OUT
 *   POST /internal/carts/{cartId}/paid           → marcar carrito PAID (pago confirmado)
 *   POST /internal/carts/{cartId}/payment-failed → revertir a ACTIVE (pago rechazado)
 */
@RestController
@RequestMapping("/internal/carts")
@RequiredArgsConstructor
@Slf4j
public class InternalCartController {

    private final CartService cartService;

    /**
     * Devuelve el resumen del carrito que necesita el payment-service
     * para crear la preferencia de pago en MercadoPago.
     *
     * Incluye: buyerId, total, moneda y lista de ítems con nombre, cantidad y precio.
     */
    @GetMapping("/{cartId}/summary")
    public ResponseEntity<InternalCartSummaryResponse> getSummary(@PathVariable String cartId) {
        log.info("[Internal] Solicitud de resumen para cartId={}", cartId);
        return ResponseEntity.ok(cartService.getCartSummaryById(cartId));
    }

    /**
     * El payment-service llama este endpoint cuando el comprador es redirigido
     * a MercadoPago. El carrito pasa a CHECKED_OUT para que no pueda modificarse
     * mientras el pago está en proceso.
     */
    @PostMapping("/{cartId}/checkout")
    public ResponseEntity<Void> checkout(@PathVariable String cartId) {
        log.info("[Internal] Marcando CHECKED_OUT cartId={}", cartId);
        cartService.markCheckedOut(cartId);
        return ResponseEntity.ok().build();
    }

    /**
     * El payment-service llama este endpoint cuando MercadoPago confirma el pago.
     * El carrito pasa a PAID — estado final exitoso del flujo de compra.
     */
    @PostMapping("/{cartId}/paid")
    public ResponseEntity<Void> paid(
            @PathVariable String cartId,
            @RequestBody PaymentResultRequest body) {
        log.info("[Internal] Marcando PAID cartId={} paymentId={}", cartId, body.getPaymentId());
        cartService.markPaid(cartId, body.getPaymentId());
        return ResponseEntity.ok().build();
    }

    /**
     * El payment-service llama este endpoint cuando el pago es rechazado o falla.
     * El carrito vuelve a ACTIVE con 30 minutos extra para que el comprador
     * pueda intentarlo de nuevo con otra tarjeta.
     */
    @PostMapping("/{cartId}/payment-failed")
    public ResponseEntity<Void> paymentFailed(
            @PathVariable String cartId,
            @RequestBody PaymentResultRequest body) {
        log.info("[Internal] Pago fallido para cartId={} — motivo={}", cartId, body.getResult());
        cartService.markPaymentFailed(cartId, body.getPaymentId(), body.getResult());
        return ResponseEntity.ok().build();
    }
}
