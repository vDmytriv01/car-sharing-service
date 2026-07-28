package com.vdmytriv.carsharing.validation;

import com.vdmytriv.carsharing.exception.InvalidRequestException;
import java.util.Set;
import org.springframework.data.domain.Pageable;

public final class PageableValidator {

    private static final int MAX_PAGE_SIZE = 100;

    private PageableValidator() {
    }

    public static void validate(Pageable pageable, Set<String> allowedSortFields) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("Page size cannot exceed " + MAX_PAGE_SIZE);
        }
        pageable.getSort().forEach(order -> {
            if (!allowedSortFields.contains(order.getProperty())) {
                throw new InvalidRequestException(
                        "Unsupported sort field: " + order.getProperty()
                );
            }
        });
    }
}
