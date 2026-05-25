package co.empresa.order_service.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class UpdateItemRequest {
    @Min(value = 1, message = "La cantidad mínima es 1")
    @Max(value = 10, message = "Máximo 10 boletas por tipo")
    private int quantity;
}
