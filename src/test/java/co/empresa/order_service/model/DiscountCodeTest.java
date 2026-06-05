package co.empresa.order_service.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitarios del modelo DiscountCode.
 *
 * No necesitan Spring ni base de datos — son pruebas de lógica pura.
 * Cubren los métodos isValid() y apply() que implementan las reglas
 * de negocio del descuento.
 */
class DiscountCodeTest {

    // ----------------------------------------------------------------
    //  Helpers
    // ----------------------------------------------------------------

    /** Crea un código de porcentaje activo sin restricciones adicionales. */
    private DiscountCode codigoPorcentajeActivo() {
        return DiscountCode.builder()
                .code("PROMO20")
                .type(DiscountCode.DiscountType.PERCENTAGE)
                .value(BigDecimal.valueOf(20))
                .active(true)
                .usedCount(0)
                .organizerId("org-1")
                .build();
    }

    // ================================================================
    //  isValid()
    // ================================================================

    @Test
    void isValid_cuandoActivoSinRestricciones_retornaTrue() {
        assertThat(codigoPorcentajeActivo().isValid()).isTrue();
    }

    @Test
    void isValid_cuandoInactivo_retornaFalse() {
        DiscountCode code = codigoPorcentajeActivo();
        code.setActive(false);
        assertThat(code.isValid()).isFalse();
    }

    @Test
    void isValid_cuandoYaVencio_retornaFalse() {
        DiscountCode code = codigoPorcentajeActivo();
        code.setExpiresAt(LocalDateTime.now().minusDays(1));
        assertThat(code.isValid()).isFalse();
    }

    @Test
    void isValid_cuandoNoHaVencidoAun_retornaTrue() {
        DiscountCode code = codigoPorcentajeActivo();
        code.setExpiresAt(LocalDateTime.now().plusDays(7));
        assertThat(code.isValid()).isTrue();
    }

    @Test
    void isValid_cuandoMaxUsosAlcanzados_retornaFalse() {
        DiscountCode code = codigoPorcentajeActivo();
        code.setMaxUses(5);
        code.setUsedCount(5);
        assertThat(code.isValid()).isFalse();
    }

    @Test
    void isValid_cuandoMaxUsosNoAlcanzados_retornaTrue() {
        DiscountCode code = codigoPorcentajeActivo();
        code.setMaxUses(5);
        code.setUsedCount(4);
        assertThat(code.isValid()).isTrue();
    }

    @Test
    void isValid_sinLimiteDeUsos_retornaTrue() {
        // maxUses null → ilimitado
        DiscountCode code = codigoPorcentajeActivo();
        code.setMaxUses(null);
        code.setUsedCount(9999);
        assertThat(code.isValid()).isTrue();
    }

    // ================================================================
    //  apply()  — descuento por PORCENTAJE
    // ================================================================

    @Test
    void apply_porcentaje_calculaCorrectamente() {
        DiscountCode code = codigoPorcentajeActivo(); // 20 %
        BigDecimal resultado = code.apply(BigDecimal.valueOf(100_000));
        // 100.000 − 20 % = 80.000
        assertThat(resultado).isEqualByComparingTo(BigDecimal.valueOf(80_000));
    }

    @Test
    void apply_porcentajeCero_noAlteraElSubtotal() {
        DiscountCode code = codigoPorcentajeActivo();
        code.setValue(BigDecimal.ZERO);
        BigDecimal resultado = code.apply(BigDecimal.valueOf(50_000));
        assertThat(resultado).isEqualByComparingTo(BigDecimal.valueOf(50_000));
    }

    @Test
    void apply_porcentaje100_resultadoEsCero() {
        DiscountCode code = codigoPorcentajeActivo();
        code.setValue(BigDecimal.valueOf(100));
        BigDecimal resultado = code.apply(BigDecimal.valueOf(60_000));
        assertThat(resultado).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ================================================================
    //  apply()  — descuento FIJO
    // ================================================================

    @Test
    void apply_fijo_calculaCorrectamente() {
        DiscountCode code = DiscountCode.builder()
                .code("VERANO10K")
                .type(DiscountCode.DiscountType.FIXED)
                .value(BigDecimal.valueOf(10_000))
                .active(true)
                .usedCount(0)
                .organizerId("org-1")
                .build();

        BigDecimal resultado = code.apply(BigDecimal.valueOf(50_000));
        // 50.000 − 10.000 = 40.000
        assertThat(resultado).isEqualByComparingTo(BigDecimal.valueOf(40_000));
    }

    @Test
    void apply_fijo_cuandoSuperaElSubtotal_retornaCero() {
        // El total nunca debe quedar negativo
        DiscountCode code = DiscountCode.builder()
                .code("MEGA")
                .type(DiscountCode.DiscountType.FIXED)
                .value(BigDecimal.valueOf(200_000))
                .active(true)
                .usedCount(0)
                .organizerId("org-1")
                .build();

        BigDecimal resultado = code.apply(BigDecimal.valueOf(50_000));
        assertThat(resultado).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
