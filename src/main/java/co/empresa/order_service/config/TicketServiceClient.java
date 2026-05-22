package co.empresa.order_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

/*
  Configuración del WebClient que apunta al ticket-service.
 */
@Configuration
class TicketWebClientConfig {

    @Bean
    public WebClient ticketServiceWebClient(
            @Value("${ticket.service.url:http://localhost:8082}") String baseUrl) {
        return WebClient.builder().baseUrl(baseUrl).build();
    }
}

/*
  Llama al ticket-service para:
  1. Verificar que el tipo de boleta existe y tiene cupos disponibles.
  2. Obtener el precio actual para guardarlo en el carrito.
 
  No accede a la BD del ticket-service — usa su API REST.
 */
@Service
public class TicketServiceClient {

    private final WebClient webClient;

    public TicketServiceClient(WebClient ticketServiceWebClient) {
        this.webClient = ticketServiceWebClient;
    }

    /*
      Obtiene la información del tipo de boleta.
      Lanza 404 si no existe, 409 si no hay cupos, 503 si el servicio no responde.
     */
    public TicketTypeInfo getTicketTypeInfo(String ticketTypeId) {
        try {
            TicketTypeInfo info = webClient.get()
                    .uri("/api/ticket-types/{id}", ticketTypeId)
                    .retrieve()
                    .onStatus(status -> status.value() == 404,
                            resp -> { throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    "Tipo de boleta no encontrado: " + ticketTypeId); })
                    .bodyToMono(TicketTypeInfo.class)
                    .block();

            if (info == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Tipo de boleta no encontrado");

            if (!info.active()) throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este tipo de boleta no está disponible para la venta");

            if (info.remainingCapacity() <= 0) throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No hay cupos disponibles para este tipo de boleta");

            return info;

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo contactar el ticket-service. Intenta de nuevo.");
        }
    }

    /* DTO mínimo con la info que necesitamos del ticket-service */
    public record TicketTypeInfo(
            String id,
            String name,
            BigDecimal price,
            int remainingCapacity,
            boolean active
    ) {}
}
