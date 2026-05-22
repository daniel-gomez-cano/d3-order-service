package co.empresa.order_service.repository;

import co.empresa.order_service.model.DiscountCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiscountCodeRepository extends JpaRepository<DiscountCode, String> {

    Optional<DiscountCode> findByCode(String code);

    boolean existsByCode(String code);

    List<DiscountCode> findByOrganizerId(String organizerId);
}
