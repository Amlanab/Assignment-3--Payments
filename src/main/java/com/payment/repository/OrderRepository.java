package com.payment.repository;

import com.payment.entity.Order;
import com.payment.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderNumber(String orderNumber);
    List<Order> findByUser(User user);
    List<Order> findByUserId(Long userId);
    boolean existsByOrderNumber(String orderNumber);
}

