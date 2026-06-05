package co.empresa.order_service.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/*
  SCRUM-40: Modelo de datos — Carrito y reserva temporal.
  El carrito expira a los 60 minutos de creado si no se hace checkout.
  Al expirar, los cupos reservados se liberan en el ticket-service.
 */
@Entity
@Table(name = "carts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /* sub de Keycloak del comprador */
    @Column(name = "buyer_id", nullable = false)
    private String buyerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CartStatus status;

    /* Momento en que el carrito expira — 60 min desde createdAt */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /* Código de descuento aplicado (puede ser null) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discount_code_id")
    private DiscountCode discountCode;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CartItem> items = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = CartStatus.ACTIVE;
        if (expiresAt == null) expiresAt = createdAt.plusMinutes(60);
    }

    @PreUpdate
    void preUpdate() { updatedAt = LocalDateTime.now(); }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /*
      Calcula el total del carrito aplicando el descuento si existe.
     */
    public BigDecimal getTotal() {
        BigDecimal subtotal = items.stream()
                .map(CartItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (discountCode == null) return subtotal;

        return discountCode.apply(subtotal);
    }

    public enum CartStatus {
        ACTIVE,       // en uso, puede modificarse
        EXPIRED,      // venció el tiempo límite, cupos liberados
        CHECKED_OUT,  // pago iniciado, en espera de confirmación del payment-service
        PAID          // pago confirmado por el payment-service — flujo completado
    }
}
