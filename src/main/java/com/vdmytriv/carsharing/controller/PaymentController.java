package com.vdmytriv.carsharing.controller;

import com.vdmytriv.carsharing.dto.PageResponse;
import com.vdmytriv.carsharing.dto.payment.PaymentCreateRequest;
import com.vdmytriv.carsharing.dto.payment.PaymentResponse;
import com.vdmytriv.carsharing.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payments", description = "Rental payment management")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Create a Stripe payment session")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(
            Principal principal,
            @RequestBody @Valid PaymentCreateRequest request
    ) {
        return paymentService.createSession(
                principal.getName(),
                request.rentalId(),
                request.type()
        );
    }

    @Operation(summary = "Get payments")
    @GetMapping
    public PageResponse<PaymentResponse> findAll(
            Principal principal,
            @RequestParam(name = "user_id", required = false) Long userId,
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable
    ) {
        return paymentService.findAll(
                principal.getName(),
                userId,
                pageable
        );
    }
}
