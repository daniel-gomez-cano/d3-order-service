package co.empresa.order_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import co.empresa.order_service.model.CartItem;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, String> {
    boolean existsByIdAndCart_Id(String id, String cartId);
}
