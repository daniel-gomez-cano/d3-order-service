package co.empresa.order_service.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/*
  SCRUM-41: Modelo de datos — Código de descuento.
    Un código de descuento puede ser de dos tipos: por porcentaje o por monto fijo. Además, puede tener un límite de usos y una fecha de vencimiento.
 */
@Entity
@Table(name = "discount_codes")
@Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DiscountCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /* El código que escribe el usuario: "PROMO20", "VERANO10K" */
    @Column(nullable = false, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiscountType type;

    /*
      El valor del descuento.
      Si type=PERCENTAGE: valor entre 0 y 100 (20 = 20%)
      Si type=FIXED: monto en pesos (10000 = $10.000)
     */
    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal value;

    /*Cuántas veces puede usarse en total (null = ilimitado) */
    @Column(name = "max_uses")
    private Integer maxUses;

    /* Cuántas veces se ha usado */
    @Column(name = "used_count", nullable = false)
    private int usedCount = 0;

    @Column(nullable = false)
    private boolean active = true;

    /* Fecha de vencimiento del código (null = sin vencimiento) */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /* ID del organizador que creó este código */
    @Column(name = "organizer_id", nullable = false)
    private String organizerId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { createdAt = LocalDateTime.now(); }

    public String getId() { return this.id; }
    public String getCode() { return this.code; }
    public DiscountType getType() { return this.type; }
    public BigDecimal getValue() { return this.value; }
    public Integer getMaxUses() { return this.maxUses; }
    public int getUsedCount() { return this.usedCount; }
    public boolean isActive() { return this.active; }
    public LocalDateTime getExpiresAt() { return this.expiresAt; }
    public String getOrganizerId() { return this.organizerId; }
    public LocalDateTime getCreatedAt() { return this.createdAt; }

    /*
     Verifica si el código puede usarse ahora.
     */
    public boolean isValid() {
        if (!active) return false;
        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) return false;
        if (maxUses != null && usedCount >= maxUses) return false;
        return true;
    }

    /*
      Aplica el descuento al subtotal dado y retorna el total final.
      Nunca retorna un valor negativo.
     */
    public BigDecimal apply(BigDecimal subtotal) {
        if (type == DiscountType.PERCENTAGE) {
            BigDecimal discount = subtotal.multiply(value)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            return subtotal.subtract(discount).max(BigDecimal.ZERO);
        } else {
            return subtotal.subtract(value).max(BigDecimal.ZERO);
        }
    }

    public enum DiscountType {
        PERCENTAGE, // porcentaje del total
        FIXED       // monto fijo en pesos
    }
}
