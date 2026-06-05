package co.empresa.order_service.service;

import co.empresa.order_service.dto.CreateDiscountCodeRequest;
import co.empresa.order_service.dto.DiscountCodeResponse;
import co.empresa.order_service.model.DiscountCode;
import co.empresa.order_service.repository.DiscountCodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

/**
 * Tests unitarios de DiscountCodeService.
 *
 * Estrategia: se mockea el repositorio con Mockito para que los tests
 * no necesiten base de datos ni contexto de Spring. Cada test verifica
 * una sola responsabilidad del servicio.
 */
@ExtendWith(MockitoExtension.class)
class DiscountCodeServiceTest {

    @Mock
    private DiscountCodeRepository repo;

    @InjectMocks
    private DiscountCodeService service;

    // ================================================================
    //  create()
    // ================================================================

    @Test
    void create_conDatosValidos_guardaYRetornaCodigo() {
        CreateDiscountCodeRequest req = new CreateDiscountCodeRequest();
        req.setCode("promo20");           // a propósito en minúsculas → se convierte a mayúsculas
        req.setType(DiscountCode.DiscountType.PERCENTAGE);
        req.setValue(BigDecimal.valueOf(20));

        when(repo.existsByCode("PROMO20")).thenReturn(false);
        when(repo.save(any(DiscountCode.class))).thenAnswer(inv -> inv.getArgument(0));

        DiscountCodeResponse result = service.create(req, "org-1");

        assertThat(result.getCode()).isEqualTo("PROMO20");
        assertThat(result.getType()).isEqualTo(DiscountCode.DiscountType.PERCENTAGE);
        verify(repo, times(1)).save(any(DiscountCode.class));
    }

    @Test
    void create_conCodigoDuplicado_lanzaConflict() {
        CreateDiscountCodeRequest req = new CreateDiscountCodeRequest();
        req.setCode("DUPLICADO");
        req.setType(DiscountCode.DiscountType.PERCENTAGE);
        req.setValue(BigDecimal.valueOf(10));

        when(repo.existsByCode("DUPLICADO")).thenReturn(true);

        assertThatThrownBy(() -> service.create(req, "org-1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(CONFLICT);

        verify(repo, never()).save(any());
    }

    @Test
    void create_conPorcentajeMayorA100_lanzaBadRequest() {
        CreateDiscountCodeRequest req = new CreateDiscountCodeRequest();
        req.setCode("INVALIDO");
        req.setType(DiscountCode.DiscountType.PERCENTAGE);
        req.setValue(BigDecimal.valueOf(150));   // > 100 %

        assertThatThrownBy(() -> service.create(req, "org-1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(BAD_REQUEST);
    }

    @Test
    void create_conMaxUsosMenorAUno_lanzaBadRequest() {
        CreateDiscountCodeRequest req = new CreateDiscountCodeRequest();
        req.setCode("ZERO");
        req.setType(DiscountCode.DiscountType.FIXED);
        req.setValue(BigDecimal.valueOf(5_000));
        req.setMaxUses(0);   // inválido

        assertThatThrownBy(() -> service.create(req, "org-1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(BAD_REQUEST);
    }

    @Test
    void create_conFechaDeVencimientoFutura_guardaCorrectamente() {
        CreateDiscountCodeRequest req = new CreateDiscountCodeRequest();
        req.setCode("VERANO");
        req.setType(DiscountCode.DiscountType.FIXED);
        req.setValue(BigDecimal.valueOf(10_000));
        req.setExpiresAt(LocalDateTime.now().plusMonths(3));

        when(repo.existsByCode("VERANO")).thenReturn(false);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DiscountCodeResponse result = service.create(req, "org-1");

        assertThat(result.getExpiresAt()).isNotNull();
        assertThat(result.getExpiresAt()).isAfter(LocalDateTime.now());
    }

    // ================================================================
    //  listByOrganizer()
    // ================================================================

    @Test
    void listByOrganizer_retornaCodigosDelOrganizador() {
        DiscountCode code1 = buildCode("CODE1", "org-1");
        DiscountCode code2 = buildCode("CODE2", "org-1");

        when(repo.findByOrganizerId("org-1")).thenReturn(List.of(code1, code2));

        List<DiscountCodeResponse> result = service.listByOrganizer("org-1");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(DiscountCodeResponse::getCode)
                .containsExactlyInAnyOrder("CODE1", "CODE2");
    }

    @Test
    void listByOrganizer_sinCodigos_retornaListaVacia() {
        when(repo.findByOrganizerId("org-sin-codigos")).thenReturn(List.of());

        List<DiscountCodeResponse> result = service.listByOrganizer("org-sin-codigos");

        assertThat(result).isEmpty();
    }

    // ================================================================
    //  toggleActive()
    // ================================================================

    @Test
    void toggleActive_cuandoPropietario_cambiaEstadoActivo() {
        DiscountCode code = buildCode("TOGGLE", "org-1");
        code.setActive(true);   // empieza activo → debe quedar inactivo

        when(repo.findById("id-1")).thenReturn(Optional.of(code));
        when(repo.save(code)).thenReturn(code);

        service.toggleActive("id-1", "org-1");

        assertThat(code.isActive()).isFalse();
        verify(repo).save(code);
    }

    @Test
    void toggleActive_cuandoNoPropietario_lanzaForbidden() {
        DiscountCode code = buildCode("TOGGLE", "org-1");
        when(repo.findById("id-1")).thenReturn(Optional.of(code));

        assertThatThrownBy(() -> service.toggleActive("id-1", "org-OTRO"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(FORBIDDEN);

        verify(repo, never()).save(any());
    }

    @Test
    void toggleActive_cuandoNoExiste_lanzaNotFound() {
        when(repo.findById("id-inexistente")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggleActive("id-inexistente", "org-1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(NOT_FOUND);
    }

    // ================================================================
    //  delete()
    // ================================================================

    @Test
    void delete_cuandoPropietario_eliminaElCodigo() {
        DiscountCode code = buildCode("DEL", "org-1");
        when(repo.findById("id-1")).thenReturn(Optional.of(code));

        service.delete("id-1", "org-1");

        verify(repo, times(1)).delete(code);
    }

    @Test
    void delete_cuandoNoPropietario_lanzaForbiddenYNoElimina() {
        DiscountCode code = buildCode("DEL", "org-1");
        when(repo.findById("id-1")).thenReturn(Optional.of(code));

        assertThatThrownBy(() -> service.delete("id-1", "org-OTRO"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(FORBIDDEN);

        verify(repo, never()).delete(any());
    }

    // ================================================================
    //  Helpers
    // ================================================================

    private DiscountCode buildCode(String codigoStr, String organizadorId) {
        DiscountCode code = DiscountCode.builder()
                .code(codigoStr)
                .type(DiscountCode.DiscountType.PERCENTAGE)
                .value(BigDecimal.valueOf(10))
                .active(true)
                .usedCount(0)
                .organizerId(organizadorId)
                .build();
        code.setId("id-1");
        return code;
    }
}
