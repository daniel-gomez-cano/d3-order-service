package co.empresa.order_service.dto;

import co.empresa.order_service.model.DiscountCode.DiscountType;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DiscountCodeResponse {
    private String id;
    private String code;
    private DiscountType type;
    private BigDecimal value;
    private Integer maxUses;
    private int usedCount;
    private boolean active;
    private LocalDateTime expiresAt;
    private String organizerId;
    private LocalDateTime createdAt;
}
