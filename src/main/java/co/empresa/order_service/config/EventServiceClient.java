package co.empresa.order_service.config;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import lombok.extern.slf4j.Slf4j;

/**
 * WebClient configurado para llamar al event-service.
 * Usado por CartService para verificar precio y disponibilidad
 * de tipos de boleta antes de agregarlos al carrito.
 */
@Configuration
class EventWebClientConfig {

    @Bean
    public WebClient eventServiceWebClient(
            @Value("${event.service.url:http://event-service:8082}") String baseUrl) {
        return WebClient.builder().baseUrl(baseUrl).build();
    }
}

@Slf4j
@Service
public class EventServiceClient {

    private final WebClient webClient;

    public EventServiceClient(WebClient eventServiceWebClient) {
        this.webClient = eventServiceWebClient;
    }

    /**
     * Consulta el event-service para obtener info de un tipo de boleta.
     * Endpoint: GET /api/internal/ticket-types/{id}
     *
     * @throws ResponseStatusException 404 si no existe
     * @throws ResponseStatusException 409 si no hay cupos o está inactivo
     * @throws ResponseStatusException 503 si el event-service no responde
     */
    public TicketTypeInfo getTicketTypeInfo(String ticketTypeId) {
        try {
            TicketTypeInfo info = webClient.get()
                    .uri("/api/internal/ticket-types/{id}", ticketTypeId)
                    .retrieve()
                    .onStatus(
                        status -> status.value() == 404,
                        resp -> Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Tipo de boleta no encontrado: " + ticketTypeId)))
                    .bodyToMono(TicketTypeInfo.class)
                    .block();

            if (info == null) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Tipo de boleta no encontrado: " + ticketTypeId);
            }
            if (!info.active()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Este tipo de boleta no está disponible para la venta");
            }
            if (info.remainingCapacity() <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "No hay cupos disponibles para este tipo de boleta");
            }

            return info;

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo conectar con el event-service: " + e.getMessage());
        }
    }

    public void reserveTickets(Long eventId, Long ticketTypeId, int quantity) {
        Map<String, Object> body = Map.of(
                "ticketTypeId", ticketTypeId,
                "quantity",     quantity
        );
        try {
            webClient.post()
                    .uri("/api/internal/events/{id}/reserve", eventId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, resp ->
                            resp.bodyToMono(String.class)
                                .flatMap(err -> Mono.error(new ResponseStatusException(
                                        resp.statusCode(), "Error reservando stock: " + err))))
                    .toBodilessEntity()
                    .block();
            log.info("[EventClient] Stock reservado — eventId={} ticketTypeId={} qty={}",
                    eventId, ticketTypeId, quantity);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo actualizar stock en event-service: " + e.getMessage());
        }
    }

    public void releaseTickets(Long eventId, Long ticketTypeId, int quantity) {
        Map<String, Object> body = Map.of(
                "ticketTypeId", ticketTypeId,
                "quantity",     quantity
        );
        try {
            webClient.post()
                    .uri("/api/internal/events/{id}/release", eventId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, resp ->
                            resp.bodyToMono(String.class)
                                .flatMap(err -> Mono.error(new ResponseStatusException(
                                        resp.statusCode(), "Error liberando stock: " + err))))
                    .toBodilessEntity()
                    .block();
            log.info("[EventClient] Stock liberado — eventId={} ticketTypeId={} qty={}",
                    eventId, ticketTypeId, quantity);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo liberar stock en event-service: " + e.getMessage());
        }
    }

    /**
     * DTO con la info que CartService necesita del event-service.
     * id es String para compatibilidad con CartItem.ticketTypeId (String).
     * Jackson deserializa el Long del event-service a String automáticamente.
     */
    public record TicketTypeInfo(
            String id,
            String name,
            BigDecimal price,
            int remainingCapacity,
            boolean active,
            Long eventId
    ) {}
}