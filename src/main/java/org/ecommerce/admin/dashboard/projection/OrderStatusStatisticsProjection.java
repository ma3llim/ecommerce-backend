package org.ecommerce.admin.dashboard.projection;

import org.ecommerce.order.enums.OrderStatus;

public interface OrderStatusStatisticsProjection {
    OrderStatus getStatus();

    Long getCount();
}
