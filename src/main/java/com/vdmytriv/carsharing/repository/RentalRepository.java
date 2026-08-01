package com.vdmytriv.carsharing.repository;

import com.vdmytriv.carsharing.model.Rental;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RentalRepository extends
        JpaRepository<Rental, Long>,
        JpaSpecificationExecutor<Rental> {

    @EntityGraph(attributePaths = "car")
    Page<Rental> findAll(Specification<Rental> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"car", "user"})
    Optional<Rental> findById(Long id);

    @EntityGraph(attributePaths = "car")
    Optional<Rental> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = {"car", "user"})
    Optional<Rental> findByIdAndUserEmail(Long id, String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rental FROM Rental rental WHERE rental.id = :id")
    Optional<Rental> findByIdForUpdate(@Param("id") Long id);
}
