package com.vdmytriv.carsharing.repository;

import com.vdmytriv.carsharing.dto.car.CarSearchCriteria;
import com.vdmytriv.carsharing.model.Car;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class CarSpecifications {

    private CarSpecifications() {
    }

    public static Specification<Car> withFilters(CarSearchCriteria criteria) {
        Specification<Car> specification = (root, query, builder) ->
                builder.isFalse(root.get("deleted"));

        if (criteria.model() != null) {
            specification = specification.and(containsIgnoreCase(
                    "model",
                    criteria.model()
            ));
        }
        if (criteria.brand() != null) {
            specification = specification.and(containsIgnoreCase(
                    "brand",
                    criteria.brand()
            ));
        }
        if (criteria.type() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("type"), criteria.type())
            );
        }
        if (criteria.available() != null) {
            specification = specification.and((root, query, builder) ->
                    criteria.available()
                            ? builder.greaterThan(root.get("inventory"), 0)
                            : builder.equal(root.get("inventory"), 0)
            );
        }
        if (criteria.minDailyFee() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.greaterThanOrEqualTo(
                            root.get("dailyFee"),
                            criteria.minDailyFee()
                    )
            );
        }
        if (criteria.maxDailyFee() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.lessThanOrEqualTo(
                            root.get("dailyFee"),
                            criteria.maxDailyFee()
                    )
            );
        }
        return specification;
    }

    private static Specification<Car> containsIgnoreCase(String field, String value) {
        String escapedValue = value.trim()
                .toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        String pattern = "%" + escapedValue + "%";
        return (root, query, builder) ->
                builder.like(builder.lower(root.get(field)), pattern, '\\');
    }
}
