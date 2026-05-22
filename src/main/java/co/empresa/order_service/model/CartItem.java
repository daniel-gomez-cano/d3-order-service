package co.empresa.order_service.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/*
  SCRUM-40: Ítem dentro del carrito.
  Representa N boletas de un mismo tipo de boleta.
 */
@Entity
@Table(name = "cart_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    /*
      ID del tipo de boleta en el ticket-service.
      Referencia externa
     */
    @Column(name = "ticket_type_id", nullable = false)
    private String ticketTypeId;

    /* Nombre del tipo de boleta (guardado para mostrar sin llamar al ticket-service) */
    @Column(name = "ticket_type_name", nullable = false)
    private String ticketTypeName;

    @Column(nullable = false)
    private int quantity;

    /*Precio unitario al momento de agregar al carrito */
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { createdAt = LocalDateTime.now(); }

    public BigDecimal getSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
