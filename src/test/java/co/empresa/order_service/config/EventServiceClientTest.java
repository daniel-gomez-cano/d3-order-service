package co.empresa.order_service.config;

import co.empresa.order_service.config.EventServiceClient.TicketTypeInfo;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventServiceClientTest {

    @Test
    void getTicketTypeInfo_retornaDatosCuandoLaRespuestaEsValida() {
        EventServiceClient client = newClient(request -> jsonResponse(
                HttpStatus.OK,
                "{\"id\":\"tt-1\",\"name\":\"VIP\",\"price\":50000,\"remainingCapacity\":10,\"active\":true,\"eventId\":100}"));

        TicketTypeInfo info = client.getTicketTypeInfo("tt-1");

        assertThat(info.id()).isEqualTo("tt-1");
        assertThat(info.name()).isEqualTo("VIP");
        assertThat(info.price()).isEqualByComparingTo(BigDecimal.valueOf(50000));
        assertThat(info.remainingCapacity()).isEqualTo(10);
        assertThat(info.active()).isTrue();
        assertThat(info.eventId()).isEqualTo(100L);
    }

    @Test
    void getTicketTypeInfo_cuandoNoExiste_lanzaNotFound() {
        EventServiceClient client = newClient(request -> jsonResponse(HttpStatus.NOT_FOUND, "{}"));

        assertThatThrownBy(() -> client.getTicketTypeInfo("tt-inexistente"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getTicketTypeInfo_cuandoEstaInactivo_lanzaConflict() {
        EventServiceClient client = newClient(request -> jsonResponse(
                HttpStatus.OK,
                "{\"id\":\"tt-1\",\"name\":\"VIP\",\"price\":50000,\"remainingCapacity\":10,\"active\":false,\"eventId\":100}"));

        assertThatThrownBy(() -> client.getTicketTypeInfo("tt-1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void getTicketTypeInfo_cuandoNoHayCapacidad_lanzaConflict() {
        EventServiceClient client = newClient(request -> jsonResponse(
                HttpStatus.OK,
                "{\"id\":\"tt-1\",\"name\":\"VIP\",\"price\":50000,\"remainingCapacity\":0,\"active\":true,\"eventId\":100}"));

        assertThatThrownBy(() -> client.getTicketTypeInfo("tt-1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void getTicketTypeInfo_cuandoLaRespuestaVieneVacia_lanzaNotFound() {
        EventServiceClient client = newClient(request -> jsonResponse(HttpStatus.OK, ""));

        assertThatThrownBy(() -> client.getTicketTypeInfo("tt-1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getTicketTypeInfo_cuandoFallaLaConexion_lanzaServiceUnavailable() {
        EventServiceClient client = newClient(request -> Mono.error(new IllegalStateException("caida")));

        assertThatThrownBy(() -> client.getTicketTypeInfo("tt-1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void reserveTickets_cuandoLaRespuestaEsExitosa_noLanzaExcepcion() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        EventServiceClient client = newClient(request -> {
            captured.set(request);
            return jsonResponse(HttpStatus.OK, "");
        });

        client.reserveTickets(100L, 1L, 3);

        assertThat(captured.get().url().getPath()).isEqualTo("/api/internal/events/100/reserve");
    }

    @Test
    void reserveTickets_cuandoElServicioRespondeError_lanzaStatusException() {
        EventServiceClient client = newClient(request -> jsonResponse(HttpStatus.BAD_REQUEST, "stock insuficiente"));

        assertThatThrownBy(() -> client.reserveTickets(100L, 1L, 3))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void releaseTickets_cuandoLaRespuestaEsExitosa_noLanzaExcepcion() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        EventServiceClient client = newClient(request -> {
            captured.set(request);
            return jsonResponse(HttpStatus.OK, "");
        });

        client.releaseTickets(200L, 2L, 1);

        assertThat(captured.get().url().getPath()).isEqualTo("/api/internal/events/200/release");
    }

    @Test
    void releaseTickets_cuandoElServicioRespondeError_lanzaStatusException() {
        EventServiceClient client = newClient(request -> jsonResponse(HttpStatus.CONFLICT, "no se pudo liberar"));

        assertThatThrownBy(() -> client.releaseTickets(200L, 2L, 1))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    private EventServiceClient newClient(ExchangeFunction exchangeFunction) {
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        return new EventServiceClient(webClient);
    }

    private Mono<ClientResponse> jsonResponse(HttpStatus status, String body) {
        ClientResponse.Builder builder = ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        if (!body.isEmpty()) {
            builder.body(body);
        }

        return Mono.just(builder.build());
    }
}