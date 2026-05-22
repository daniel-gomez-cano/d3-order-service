package co.empresa.order_service.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.empresa.order_service.dto.CreateDiscountCodeRequest;
import co.empresa.order_service.dto.DiscountCodeResponse;
import co.empresa.order_service.service.DiscountCodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Gestión de códigos de descuento — solo ORGANIZER y ADMIN.
 *
 * POST   /api/discount-codes = crear código
 * GET    /api/discount-codes = listar mis códigos
 * PATCH  /api/discount-codes/{id}/toggle = activar/desactivar
 * DELETE /api/discount-codes/{id} = eliminar
 */
@RestController
@RequestMapping("/api/discount-codes")
@RequiredArgsConstructor
public class DiscountCodeController {

    private final DiscountCodeService service;

    @PostMapping
    @PreAuthorize("hasRole('ROLE_ORGANIZER') or hasRole('ROLE_ADMIN')")
    public ResponseEntity<DiscountCodeResponse> create(
            @Valid @RequestBody CreateDiscountCodeRequest req,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(req, jwt.getSubject()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ROLE_ORGANIZER') or hasRole('ROLE_ADMIN')")
    public ResponseEntity<List<DiscountCodeResponse>> list(
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(service.listByOrganizer(jwt.getSubject()));
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ROLE_ORGANIZER') or hasRole('ROLE_ADMIN')")
    public ResponseEntity<DiscountCodeResponse> toggle(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(service.toggleActive(id, jwt.getSubject()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ORGANIZER') or hasRole('ROLE_ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {

        service.delete(id, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}
