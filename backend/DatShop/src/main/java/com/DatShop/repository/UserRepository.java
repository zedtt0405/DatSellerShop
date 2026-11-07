package com.DatShop.repository;

import com.DatShop.model.RoleName;
import com.DatShop.model.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByUsername(String username);

    @EntityGraph(attributePaths = "roles")
    @Query("SELECT u FROM User u WHERE u.username = :username")
    Optional<User> findWithRolesByUsername(@Param("username") String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT COUNT(u) > 0
            FROM User u
            JOIN u.roles r
            WHERE u.id = :id AND r.name = :role
            """)
    boolean existsByIdAndRoleName(@Param("id") Long id, @Param("role") RoleName role);

    @Query("SELECT COUNT(DISTINCT u.id) FROM User u JOIN u.roles r WHERE r.name = :role")
    long countByRoleName(@Param("role") RoleName role);
}
