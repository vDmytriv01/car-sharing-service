package com.vdmytriv.carsharing.repository;

import com.vdmytriv.carsharing.model.Payment;
import com.vdmytriv.carsharing.model.PaymentType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Override
    @EntityGraph(attributePaths = {"rental", "rental.user"})
    Page<Payment> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"rental", "rental.user"})
    Page<Payment> findAllByRentalUserId(Long userId, Pageable pageable);

    boolean existsBySessionId(String sessionId);

    @EntityGraph(attributePaths = {"rental", "rental.user"})
    Optional<Payment> findByRentalIdAndType(
            Long rentalId,
            PaymentType type
    );

    @EntityGraph(attributePaths = {"rental", "rental.user"})
    Optional<Payment> findBySessionId(String sessionId);

    @Modifying
    @Transactional
    @Query("""
            UPDATE Payment payment
            SET payment.status = com.vdmytriv.carsharing.model.PaymentStatus.PAID
            WHERE payment.sessionId = :sessionId
              AND payment.status = com.vdmytriv.carsharing.model.PaymentStatus.PENDING
            """)
    int markPaid(@Param("sessionId") String sessionId);
}
