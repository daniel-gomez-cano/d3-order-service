package co.empresa.order_service.dto;

import co.empresa.order_service.model.DiscountCode.DiscountType;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CreateDiscountCodeRequest {
    @NotBlank
    @Size(min = 3, max = 20)
    private String code;

    @NotNull
    private DiscountType type;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal value;

    private Integer maxUses;
    private LocalDateTime expiresAt;
}
