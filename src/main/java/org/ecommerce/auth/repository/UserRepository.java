package org.ecommerce.auth.repository;

import org.ecommerce.admin.dashboard.projection.UserMonthlyStatisticsProjection;
import org.ecommerce.auth.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);

    boolean existsByPhoneNumberIgnoreCaseAndIdNot(String phoneNumber, UUID userId);

    @Query(value = """
            SELECT TO_CHAR(created_at, 'YYYY-MM') AS period, COUNT(*) AS count FROM users
            GROUP BY TO_CHAR(created_at, 'YYYY-MM')
            ORDER BY period
            """, nativeQuery = true)
    List<UserMonthlyStatisticsProjection> getMonthlyUserStatistics();
}
