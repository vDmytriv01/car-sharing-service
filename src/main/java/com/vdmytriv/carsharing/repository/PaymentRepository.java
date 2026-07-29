package com.vdmytriv.carsharing.repository;

import com.vdmytriv.carsharing.model.Payment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findBySessionId(String sessionId);
}
