package com.vdmytriv.carsharing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "cars")
@SQLDelete(sql = "UPDATE cars SET is_deleted = TRUE WHERE id = ?")
public class Car {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(nullable = false, length = 100)
    private String brand;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CarType type;

    @Column(nullable = false)
    private int inventory;

    @Column(name = "daily_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal dailyFee;

    @Column(name = "is_deleted", nullable = false)
    @Setter(AccessLevel.NONE)
    private boolean deleted;
}
