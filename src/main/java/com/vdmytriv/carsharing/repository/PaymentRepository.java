package com.vdmytriv.carsharing.repository;

import com.vdmytriv.carsharing.model.Payment;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Override
    @EntityGraph(attributePaths = {"rental", "rental.user"})
    Page<Payment> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"rental", "rental.user"})
    Page<Payment> findAllByRentalUserId(Long userId, Pageable pageable);

    Optional<Payment> findBySessionId(String sessionId);
}
