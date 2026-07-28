package com.vdmytriv.carsharing.repository;

import com.vdmytriv.carsharing.model.Rental;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RentalRepository extends
        JpaRepository<Rental, Long>,
        JpaSpecificationExecutor<Rental> {

    @EntityGraph(attributePaths = "car")
    Page<Rental> findAll(Specification<Rental> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "car")
    Optional<Rental> findById(Long id);

    @EntityGraph(attributePaths = "car")
    Optional<Rental> findByIdAndUserId(Long id, Long userId);
}
