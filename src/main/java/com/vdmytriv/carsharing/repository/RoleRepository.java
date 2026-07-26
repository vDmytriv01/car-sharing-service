package com.vdmytriv.carsharing.repository;

import com.vdmytriv.carsharing.model.Role;
import com.vdmytriv.carsharing.model.RoleName;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleName name);
}
