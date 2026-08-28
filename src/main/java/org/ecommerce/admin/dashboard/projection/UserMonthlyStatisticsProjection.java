package org.ecommerce.admin.dashboard.projection;

public interface UserMonthlyStatisticsProjection {
    String getPeriod();

    Long getCount();
}
