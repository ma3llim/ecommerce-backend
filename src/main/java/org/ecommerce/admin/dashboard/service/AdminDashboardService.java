package org.ecommerce.admin.dashboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ecommerce.admin.dashboard.dto.DashboardSummaryResponse;
import org.ecommerce.admin.dashboard.dto.OrderStatisticsResponse;
import org.ecommerce.admin.dashboard.dto.ProductCategoryStatisticsResponse;
import org.ecommerce.admin.dashboard.dto.UserStatisticsResponse;
import org.ecommerce.auth.repository.UserRepository;
import org.ecommerce.catelog.repository.CategoryRepository;
import org.ecommerce.catelog.repository.ProductRepository;
import org.ecommerce.coupon.repository.CouponRepository;
import org.ecommerce.order.repository.OrderRepository;
import org.ecommerce.order.repository.PaymentRepository;
import org.ecommerce.review.repository.ReviewRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDashboardService {
    private final UserRepository adminDashboardUserRepository;
    private final ProductRepository adminDashboardProductRepository;
    private final CategoryRepository adminDashboardCategoryRepository;
    private final OrderRepository adminDashboardOrderRepository;
    private final ReviewRepository adminDashboardReviewRepository;
    private final CouponRepository adminDashboardCouponRepository;
    private final PaymentRepository adminDashboardPaymentRepository;

    public DashboardSummaryResponse getSummary() {

        DashboardSummaryResponse response = DashboardSummaryResponse.builder()
                .totalUsers(adminDashboardUserRepository.count())
                .totalProducts(adminDashboardProductRepository.count())
                .totalCategories(adminDashboardCategoryRepository.count())
                .totalOrders(adminDashboardOrderRepository.count())
                .totalReviews(adminDashboardReviewRepository.count())
                .totalCoupons(adminDashboardCouponRepository.count())
                .totalPayments(adminDashboardPaymentRepository.count())
                .build();

        log.info("Admin dashboard summary fetched successfully: users={}, products={}, orders={}",
                response.totalUsers(),
                response.totalProducts(),
                response.totalOrders()
        );

        return response;
    }

    public List<UserStatisticsResponse> getUserStatistics() {

        return adminDashboardUserRepository.getMonthlyUserStatistics()
                .stream()
                .map(stat -> UserStatisticsResponse.builder()
                        .period(stat.getPeriod())
                        .count(stat.getCount())
                        .build())
                .toList();
    }

    public List<OrderStatisticsResponse> getOrderStatistics() {

        return adminDashboardOrderRepository.getOrderStatusStatistics().stream()
                .map(stat -> OrderStatisticsResponse.builder()
                        .status(stat.getStatus().name())
                        .count(stat.getCount())
                        .build())
                .toList();
    }

    public List<ProductCategoryStatisticsResponse> getProductCategoryStatistics() {

        return adminDashboardProductRepository.getProductCategoryStatistics()
                .stream()
                .map(stat -> ProductCategoryStatisticsResponse.builder()
                        .category(stat.getCategory())
                        .count(stat.getCount())
                        .build())
                .toList();
    }
}
