package co.empresa.order_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.empresa.order_service.dto.AddItemRequest;
import co.empresa.order_service.dto.ApplyDiscountRequest;
import co.empresa.order_service.dto.CartResponse;
import co.empresa.order_service.dto.CartSummaryResponse;
import co.empresa.order_service.dto.UpdateItemRequest;
import co.empresa.order_service.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * SCRUM-42: GET/POST  /api/cart=obtener o crear carrito activo
 * SCRUM-43: POST      /api/cart/items= agregar ítem
 * SCRUM-44: PUT       /api/cart/items/{id}= actualizar cantidad
 * SCRUM-45: DELETE    /api/cart/items/{id}= eliminar ítem
 * SCRUM-47: POST      /api/cart/discount = aplicar código de descuento
 * SCRUM-48: GET       /api/cart/summary= resumen con totales
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService service;

    // SCRUM-42
    @GetMapping
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<CartResponse> getOrCreate(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(service.getOrCreateCart(jwt.getSubject()));
    }

    // SCRUM-43
    @PostMapping("/items")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<CartResponse> addItem(
            @Valid @RequestBody AddItemRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(service.addItem(jwt.getSubject(), req));
    }

    // SCRUM-44
    @PutMapping("/items/{itemId}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<CartResponse> updateItem(
            @PathVariable String itemId,
            @Valid @RequestBody UpdateItemRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(service.updateItem(jwt.getSubject(), itemId, req));
    }

    // SCRUM-45
    @DeleteMapping("/items/{itemId}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<CartResponse> removeItem(
            @PathVariable String itemId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(service.removeItem(jwt.getSubject(), itemId));
    }

    // SCRUM-47
    @PostMapping("/discount")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<CartResponse> applyDiscount(
            @Valid @RequestBody ApplyDiscountRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(service.applyDiscount(jwt.getSubject(), req));
    }

    // SCRUM-48
    @GetMapping("/summary")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<CartSummaryResponse> getSummary(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(service.getSummary(jwt.getSubject()));
    }
}
