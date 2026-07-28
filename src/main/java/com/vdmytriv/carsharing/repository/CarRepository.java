package com.vdmytriv.carsharing.repository;

import com.vdmytriv.carsharing.model.Car;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CarRepository extends
        JpaRepository<Car, Long>,
        JpaSpecificationExecutor<Car> {

    Optional<Car> findByIdAndDeletedFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT car FROM Car car WHERE car.id = :id AND car.deleted = false")
    Optional<Car> findByIdForUpdate(@Param("id") Long id);

    Page<Car> findAllByDeletedFalse(Pageable pageable);
}
