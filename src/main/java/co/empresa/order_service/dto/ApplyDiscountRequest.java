package co.empresa.order_service.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ApplyDiscountRequest {
    @NotBlank(message = "El código de descuento es obligatorio")
    private String code;
}
