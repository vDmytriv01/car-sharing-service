package com.vdmytriv.carsharing.config;

import com.vdmytriv.carsharing.exception.InvalidRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PaginationConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new PaginationRequestInterceptor())
                .addPathPatterns("/cars", "/rentals", "/payments");
    }

    private static final class PaginationRequestInterceptor
            implements HandlerInterceptor {

        @Override
        public boolean preHandle(
                HttpServletRequest request,
                HttpServletResponse response,
                Object handler
        ) {
            if (!HttpMethod.GET.matches(request.getMethod())) {
                return true;
            }
            validateMinimum(
                    request.getParameter("page"),
                    0,
                    "Page number cannot be negative"
            );
            validateMinimum(
                    request.getParameter("size"),
                    1,
                    "Page size must be greater than zero"
            );
            return true;
        }

        private void validateMinimum(
                String value,
                int minimum,
                String message
        ) {
            if (value == null) {
                return;
            }
            try {
                if (Integer.parseInt(value) < minimum) {
                    throw new InvalidRequestException(message);
                }
            } catch (NumberFormatException exception) {
                throw new InvalidRequestException(
                        "Pagination parameters must be integers"
                );
            }
        }
    }
}
