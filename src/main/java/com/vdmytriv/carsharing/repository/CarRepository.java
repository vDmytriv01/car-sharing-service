package com.vdmytriv.carsharing.repository;

import com.vdmytriv.carsharing.model.Car;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CarRepository extends
        JpaRepository<Car, Long>,
        JpaSpecificationExecutor<Car> {

    Optional<Car> findByIdAndDeletedFalse(Long id);

    Page<Car> findAllByDeletedFalse(Pageable pageable);
}
