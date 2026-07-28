package com.vdmytriv.carsharing.repository;

import com.vdmytriv.carsharing.model.Rental;
import org.springframework.data.jpa.domain.Specification;

public final class RentalSpecifications {

    private RentalSpecifications() {
    }

    public static Specification<Rental> withFilters(Long userId, Boolean active) {
        Specification<Rental> specification = Specification.unrestricted();
        if (userId != null) {
            specification = specification.and(hasUserId(userId));
        }
        if (active != null) {
            specification = specification.and(isActive(active));
        }
        return specification;
    }

    private static Specification<Rental> hasUserId(Long userId) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("user").get("id"), userId);
    }

    private static Specification<Rental> isActive(boolean active) {
        return (root, query, criteriaBuilder) -> active
                ? criteriaBuilder.isNull(root.get("actualReturnDate"))
                : criteriaBuilder.isNotNull(root.get("actualReturnDate"));
    }
}
