package co.empresa.order_service.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import co.empresa.order_service.model.DiscountCode.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CreateDiscountCodeRequest {
    @NotBlank
    @Size(min = 3, max = 20)
    private String code;

    @NotNull
    private DiscountType type;

    @NotNull
    @DecimalMin(value = "0.01", message = "El valor debe ser mayor a 0")
    private BigDecimal value;

    @Min(value = 1, message = "El número máximo de usos debe ser al menos 1")
    private Integer maxUses;
    private LocalDateTime expiresAt;
}
