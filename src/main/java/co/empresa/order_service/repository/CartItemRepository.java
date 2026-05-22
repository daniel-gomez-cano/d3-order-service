package co.empresa.order_service.repository;

import co.empresa.order_service.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, String> {
    boolean existsByIdAndCartId(String id, String cartId);
}
