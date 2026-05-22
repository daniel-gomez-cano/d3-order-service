package co.empresa.order_service.service;

import co.empresa.order_service.dto.CreateDiscountCodeRequest;
import co.empresa.order_service.dto.DiscountCodeResponse;
import co.empresa.order_service.model.DiscountCode;
import co.empresa.order_service.repository.DiscountCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DiscountCodeService {

    private final DiscountCodeRepository repo;

    /* Crear un código de descuento (solo ORGANIZER/ADMIN) */
    @Transactional
    public DiscountCodeResponse create(CreateDiscountCodeRequest req, String organizerId) {
        if (repo.existsByCode(req.getCode().toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un código con ese nombre");
        }

        DiscountCode code = DiscountCode.builder()
                .code(req.getCode().toUpperCase())
                .type(req.getType())
                .value(req.getValue())
                .maxUses(req.getMaxUses())
                .expiresAt(req.getExpiresAt())
                .organizerId(organizerId)
                .active(true)
                .usedCount(0)
                .build();

        return toResponse(repo.save(code));
    }

    /* Listar todos los códigos del organizador */
    public List<DiscountCodeResponse> listByOrganizer(String organizerId) {
        return repo.findByOrganizerId(organizerId).stream()
                .map(this::toResponse)
                .toList();
    }

    /* Activar o desactivar un código */
    @Transactional
    public DiscountCodeResponse toggleActive(String id, String organizerId) {
        DiscountCode code = findAndVerifyOwnership(id, organizerId);
        code.setActive(!code.isActive());
        return toResponse(repo.save(code));
    }

    /* Eliminar un código */
    @Transactional
    public void delete(String id, String organizerId) {
        repo.delete(findAndVerifyOwnership(id, organizerId));
    }

    private DiscountCode findAndVerifyOwnership(String id, String organizerId) {
        DiscountCode code = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Código de descuento no encontrado"));
        if (!code.getOrganizerId().equals(organizerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No tienes permiso para modificar este código");
        }
        return code;
    }

    private DiscountCodeResponse toResponse(DiscountCode c) {
        return DiscountCodeResponse.builder()
                .id(c.getId())
                .code(c.getCode())
                .type(c.getType())
                .value(c.getValue())
                .maxUses(c.getMaxUses())
                .usedCount(c.getUsedCount())
                .active(c.isActive())
                .expiresAt(c.getExpiresAt())
                .organizerId(c.getOrganizerId())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
