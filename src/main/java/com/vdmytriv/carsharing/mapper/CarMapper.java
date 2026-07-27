package com.vdmytriv.carsharing.mapper;

import com.vdmytriv.carsharing.dto.car.CarCreateRequest;
import com.vdmytriv.carsharing.dto.car.CarPatchRequest;
import com.vdmytriv.carsharing.dto.car.CarResponse;
import com.vdmytriv.carsharing.dto.car.CarUpdateRequest;
import com.vdmytriv.carsharing.model.Car;
import org.springframework.stereotype.Component;

@Component
public class CarMapper {

    public Car toModel(CarCreateRequest request) {
        Car car = new Car();
        car.setModel(request.model().trim());
        car.setBrand(request.brand().trim());
        car.setType(request.type());
        car.setInventory(request.inventory());
        car.setDailyFee(request.dailyFee());
        return car;
    }

    public void updateModel(Car car, CarUpdateRequest request) {
        car.setModel(request.model().trim());
        car.setBrand(request.brand().trim());
        car.setType(request.type());
        car.setInventory(request.inventory());
        car.setDailyFee(request.dailyFee());
    }

    public void patchModel(Car car, CarPatchRequest request) {
        if (request.model() != null) {
            car.setModel(request.model().trim());
        }
        if (request.brand() != null) {
            car.setBrand(request.brand().trim());
        }
        if (request.type() != null) {
            car.setType(request.type());
        }
        if (request.inventory() != null) {
            car.setInventory(request.inventory());
        }
        if (request.dailyFee() != null) {
            car.setDailyFee(request.dailyFee());
        }
    }

    public CarResponse toResponse(Car car) {
        return new CarResponse(
                car.getId(),
                car.getModel(),
                car.getBrand(),
                car.getType(),
                car.getInventory(),
                car.getDailyFee()
        );
    }
}
