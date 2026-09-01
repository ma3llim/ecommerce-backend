package org.ecommerce.order.service.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ecommerce.auth.entities.User;
import org.ecommerce.auth.repository.UserRepository;
import org.ecommerce.catelog.repository.ProductVariantRepository;
import org.ecommerce.common.dtos.PageResponse;
import org.ecommerce.common.exception.BadRequestException;
import org.ecommerce.common.exception.ResourceNotFoundException;
import org.ecommerce.common.notification.dtos.NotificationRequest;
import org.ecommerce.common.notification.enums.channel.NotificationChannel;
import org.ecommerce.common.notification.enums.channel.NotificationEvent;
import org.ecommerce.common.notification.service.NotificationService;
import org.ecommerce.common.utils.DateTimeUtil;
import org.ecommerce.order.dtos.response.*;
import org.ecommerce.order.entities.Order;
import org.ecommerce.order.entities.OrderItem;
import org.ecommerce.order.entities.Payment;
import org.ecommerce.order.enums.OrderStatus;
import org.ecommerce.order.enums.PaymentMethod;
import org.ecommerce.order.enums.PaymentStatus;
import org.ecommerce.order.repository.OrderItemRepository;
import org.ecommerce.order.repository.OrderRepository;
import org.ecommerce.order.repository.PaymentRepository;
import org.ecommerce.order.service.PaymentService;
import org.ecommerce.user.entity.UserAddress;
import org.ecommerce.user.repository.UserAddressRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminOrderService {
    private final UserAddressRepository userAddressRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;
    private final PaymentService paymentService;
    private final ProductVariantRepository productVariantRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public PageResponse<OrderResponse> getAllOrders(String search, String orderStatus, String paymentStatus, Pageable pageable) {
        OrderStatus status = null;
        if (orderStatus != null && !orderStatus.isBlank()) {
            try {
                status = OrderStatus.valueOf(orderStatus.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Get orders failed: invalid order status. orderStatus={}", orderStatus);
                throw new BadRequestException("Invalid order status: " + orderStatus);
            }
        }

        PaymentStatus paymentStatusEnum = null;

        if (paymentStatus != null && !paymentStatus.isBlank()) {
            try {
                paymentStatusEnum = PaymentStatus.valueOf(paymentStatus.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Get orders failed: invalid payment status. paymentStatus={}", paymentStatus);
                throw new BadRequestException("Invalid payment status: " + paymentStatus);
            }
        }

        Page<Order> orders = orderRepository.findOrders(search, status, paymentStatusEnum, pageable);

        List<OrderResponse> content = orders.getContent().stream().map(order ->
                objectMapper.convertValue(order, OrderResponse.class)).toList();

        return PageResponse.<OrderResponse>builder()
                .content(content)
                .page(orders.getNumber())
                .size(orders.getSize())
                .totalElements(orders.getTotalElements())
                .totalPages(orders.getTotalPages())
                .first(orders.isFirst())
                .last(orders.isLast())
                .build();
    }

    public OrderDetailResponse getOrderDetails(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> {
            log.warn("Get order details failed: order not found. orderId={}", orderId);
            return new ResourceNotFoundException("Order not found");
        });

        List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());
        List<OrderItemResponse> items = orderItems.stream().map(item ->
                objectMapper.convertValue(item, OrderItemResponse.class)).toList();

        Payment payment = paymentRepository.findByOrderId(order.getId()).orElse(null);

        PaymentResponse paymentResponse = payment == null ? null :
                objectMapper.convertValue(payment, PaymentResponse.class);

        UserAddress shippingAddress = userAddressRepository.findById(order.getShippingAddressId()).orElseThrow(() -> {
            log.warn("Shipping address not found: orderId={}, addressId={}", orderId, order.getShippingAddressId());
            return new ResourceNotFoundException("Shipping address not found");
        });
        AddressResponse addressResponse = objectMapper.convertValue(shippingAddress, AddressResponse.class);

        return OrderDetailResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .subtotal(calculateSubtotal(orderItems))
                .shippingAmount(order.getShippingAmount())
                .discountAmount(order.getDiscountAmount())
                .taxAmount(order.getTaxAmount())
                .totalAmount(order.getTotalAmount())
                .paymentStatus(order.getPaymentStatus())
                .orderStatus(order.getOrderStatus())
                .items(items)
                .payment(paymentResponse)
                .shippingAddress(addressResponse)
                .build();
    }


    private BigDecimal calculateSubtotal(List<OrderItem> orderItems) {
        return orderItems.stream().map(OrderItem::getTotalPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public OrderResponse updateOrderStatus(UUID orderId, OrderStatus status) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> {
            log.warn("Order not found while updating status: orderId={}", orderId);
            return new ResourceNotFoundException("Order not found");
        });

        OrderStatus currentStatus = order.getOrderStatus();

        if (currentStatus == status) {
            log.warn("Update order status rejected: order already has requested status. " +
                    "orderId={}, status={}", orderId, status);
            throw new BadRequestException("Order is already in " + status + " status");
        }

        boolean validStatus = switch (currentStatus) {
            case PENDING -> status == OrderStatus.CONFIRMED || status == OrderStatus.CANCELLED;
            case CONFIRMED -> status == OrderStatus.PACKED || status == OrderStatus.CANCELLED;
            case PACKED -> status == OrderStatus.SHIPPED;
            case SHIPPED -> status == OrderStatus.DELIVERED;
            case DELIVERED -> status == OrderStatus.RETURNED;
            case CANCELLED, RETURNED -> false;
        };

        if (!validStatus) {
            log.warn("Invalid order status transition: orderId={}, currentStatus={}, requestedStatus={}",
                    orderId, currentStatus, status);
            throw new BadRequestException("Invalid order status transition: " + currentStatus + " -> " + status);
        }

        order.setOrderStatus(status);

        Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow(() -> {
            log.warn("Payment not found while updating order status: orderId={}", orderId);
            return new ResourceNotFoundException("Payment not found");
        });

        if (status == OrderStatus.DELIVERED && payment.getPaymentMethod() == PaymentMethod.COD) {
            payment.setPaymentStatus(PaymentStatus.SUCCESS);
            order.setPaymentStatus(PaymentStatus.SUCCESS);

            paymentRepository.save(payment);
        }

        orderRepository.save(order);

        return objectMapper.convertValue(order, OrderResponse.class);
    }

    @Transactional
    public OrderResponse cancelOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> {
            log.warn("Cancel order failed: order not found, orderId={}", orderId
            );
            return new ResourceNotFoundException("Order not found");
        });

        User user = userRepository.findById(order.getUserId()).orElseThrow(() -> {
            log.warn("Cancel order failed: user not found. orderId={}, userId={}", orderId, order.getUserId());
            return new ResourceNotFoundException("User not found");
        });

        if (order.getOrderStatus() != OrderStatus.PENDING && order.getOrderStatus() != OrderStatus.CONFIRMED) {
            log.warn("Order cancellation rejected: orderId={}, status={}", orderId, order.getOrderStatus());
            throw new BadRequestException("Order cannot be cancelled at this stage");
        }

        Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow(() -> {
            log.warn("Cancel order failed: payment not found, orderId={}", orderId);
            return new ResourceNotFoundException("Payment not found");
        });

        if (payment.getPaymentStatus() == PaymentStatus.CAPTURED) {
            log.info("Refund initiated for cancelled order. orderId={}, transactionId={}",
                    orderId, payment.getTransactionId());

            paymentService.refundPayment(payment);

            order.setPaymentStatus(PaymentStatus.REFUNDED);
        }

        int restoredRows = productVariantRepository.restoreStock(order.getId());

        if (restoredRows == 0) {
            log.warn("Stock restoration failed: orderId={}", orderId);
            throw new BadRequestException("Unable to restore stock");
        }

        order.setOrderStatus(OrderStatus.CANCELLED);

        orderRepository.save(order);

        sendOrderCancelledMail(user.getFullName(), order.getOrderNumber(), order.getTotalAmount(), Instant.now(),
                user.getEmail());

        return objectMapper.convertValue(order, OrderResponse.class);
    }

    public void sendOrderCancelledMail(
            String fullName, String orderNumber, BigDecimal amount, Instant cancelledAt, String recipientEmail
    ) {
        Map<String, Object> data = new HashMap<>();
        data.put("customerName", fullName);
        data.put("orderNumber", orderNumber);
        data.put("amount", amount);
        data.put("cancellationReason", "Order cancelled by administrator");
        data.put("paidAt", DateTimeUtil.format(cancelledAt));

        NotificationRequest request = NotificationRequest.builder()
                .channel(NotificationChannel.EMAIL)
                .event(NotificationEvent.ORDER_CANCELLED)
                .recipient(recipientEmail)
                .data(data)
                .build();

        notificationService.send(request);
    }
}
