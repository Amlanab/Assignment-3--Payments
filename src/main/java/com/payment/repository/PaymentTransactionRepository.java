package com.payment.repository;

import com.payment.entity.PaymentTransaction;
import com.payment.enums.TransactionStatus;
import com.payment.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByAuthorizeNetTransactionId(String authorizeNetTransactionId);
    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);
    List<PaymentTransaction> findByOrderId(Long orderId);
    List<PaymentTransaction> findByParentTransactionId(Long parentTransactionId);
    
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.order.id = :orderId AND pt.transactionType = :type AND pt.status = :status")
    List<PaymentTransaction> findByOrderIdAndTypeAndStatus(
            @Param("orderId") Long orderId,
            @Param("type") TransactionType type,
            @Param("status") TransactionStatus status
    );
    
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.parentTransaction.id = :parentId AND pt.transactionType = :type")
    List<PaymentTransaction> findByParentIdAndType(
            @Param("parentId") Long parentId,
            @Param("type") TransactionType type
    );
}

