package co.empresa.order_service.controller;

import co.empresa.order_service.service.CheckoutService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * POST /api/cart/checkout — el cliente confirma el carrito e inicia el pago.
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutService checkoutService;

    @PostMapping("/checkout")
    @PreAuthorize("hasAuthority('ROLE_CLIENT')")
    public ResponseEntity<Map<String, String>> checkout(
            @org.springframework.web.bind.annotation.RequestBody CheckoutRequest req,
            @AuthenticationPrincipal Jwt jwt) {

        String buyerId = jwt.getSubject();
        // El email puede venir en el token de Keycloak o en el body
        String buyerEmail = req.getEmail() != null
                ? req.getEmail()
                : jwt.getClaimAsString("email");

        checkoutService.initiateCheckout(buyerId, buyerEmail);

        return ResponseEntity.accepted().body(Map.of(
                "message", "Checkout iniciado. Serás redirigido al pago.",
                "status", "PROCESSING"
        ));
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class CheckoutRequest {
        @Email(message = "Email inválido")
        private String email; // opcional si viene en el JWT
    }
}
