package com.vdmytriv.carsharing.repository;

import com.vdmytriv.carsharing.model.Rental;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RentalRepository extends
        JpaRepository<Rental, Long>,
        JpaSpecificationExecutor<Rental> {

    Optional<Rental> findByIdAndUserId(Long id, Long userId);
}
