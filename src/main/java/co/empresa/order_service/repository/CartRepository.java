package co.empresa.order_service.repository;

import co.empresa.order_service.model.Cart;
import co.empresa.order_service.model.Cart.CartStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, String> {

    /* Busca el carrito ACTIVE del comprador — cada usuario tiene máximo uno activo */
    Optional<Cart> findByBuyerIdAndStatus(String buyerId, CartStatus status);

    /* Para el job de expiración: todos los carritos activos cuyo tiempo venció */
    List<Cart> findByStatusAndExpiresAtBefore(CartStatus status, LocalDateTime now);
}
