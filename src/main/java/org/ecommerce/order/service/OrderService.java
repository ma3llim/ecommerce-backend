package org.ecommerce.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ecommerce.auth.entities.User;
import org.ecommerce.auth.repository.UserRepository;
import org.ecommerce.cart.entities.Cart;
import org.ecommerce.cart.entities.CartItem;
import org.ecommerce.cart.repository.CartItemRepository;
import org.ecommerce.cart.repository.CartRepository;
import org.ecommerce.cart.repository.projection.CartOrderItemProjection;
import org.ecommerce.catelog.entities.ProductVariantImage;
import org.ecommerce.catelog.repository.ProductVariantImageRepository;
import org.ecommerce.catelog.repository.ProductVariantRepository;
import org.ecommerce.common.dtos.PageResponse;
import org.ecommerce.common.enums.DiscountType;
import org.ecommerce.common.exception.BadRequestException;
import org.ecommerce.common.exception.ResourceNotFoundException;
import org.ecommerce.common.notification.dtos.NotificationRequest;
import org.ecommerce.common.notification.dtos.OrderItemEmailData;
import org.ecommerce.common.notification.enums.channel.NotificationChannel;
import org.ecommerce.common.notification.enums.channel.NotificationEvent;
import org.ecommerce.common.notification.service.NotificationService;
import org.ecommerce.common.utils.DateTimeUtil;
import org.ecommerce.coupon.entities.Coupon;
import org.ecommerce.coupon.repository.CouponRepository;
import org.ecommerce.order.dtos.request.CreateOrderRequest;
import org.ecommerce.order.dtos.response.*;
import org.ecommerce.order.entities.*;
import org.ecommerce.order.enums.OrderStatus;
import org.ecommerce.order.enums.PaymentMethod;
import org.ecommerce.order.enums.PaymentStatus;
import org.ecommerce.order.repository.*;
import org.ecommerce.user.entity.UserAddress;
import org.ecommerce.user.repository.UserAddressRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderService {
    private final UserRepository userRepository;
    private final UserAddressRepository userAddressRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final CouponRepository couponRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;
    private final PaymentService paymentService;
    private final ProductVariantRepository productVariantRepository;
    private final ProductVariantImageRepository productVariantImageRepository;
    private final OrderFinalizationService orderFinalizationService;
    private final ShipmentTrackingEventRepository shipmentTrackingEventRepository;
    private final ShipmentRepository shipmentRepository;
    private final NotificationService notificationService;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, Authentication authentication) {
        UUID userId = ((User) authentication.getPrincipal()).getId();
        User user = userRepository.findById(userId).orElseThrow(() -> {
            log.warn("Create order failed: user not found, userId={}", userId);
            return new ResourceNotFoundException("User not found");
        });

        UserAddress address = userAddressRepository.findByUserIdAndId(user.getId(), request.shippingAddressId()).orElseThrow(() -> {
            log.warn("Create order failed: address not found, addressId={}, userId={}", request.shippingAddressId(), userId);
            return new ResourceNotFoundException("User address not found");
        });

        Cart cart = cartRepository.findByUserId(user.getId()).orElseThrow(() -> {
            log.warn("Create order failed: cart not found, userId={}", userId);
            return new ResourceNotFoundException("User cart not found");
        });

        List<CartItem> cartItems = cartItemRepository.findByCartId(cart.getId());
        if (cartItems.isEmpty()) {
            log.warn("Create order rejected: cart is empty. userId={}, cartId={}", userId, cart.getId());
            throw new BadRequestException("Cannot create order from an empty cart");
        }

        List<CartOrderItemProjection> items = cartItemRepository.findOrderItemsByCartId(cart.getId());

        if (items.size() != cartItems.size()) {
            log.warn("Create order rejected: one or more cart items are unavailable. userId={}, cartId={}, " +
                    "cartItems={}, availableItems={}", userId, cart.getId(), cartItems.size(), items.size());
            throw new BadRequestException("One or more products in your cart are no longer available");
        }

        BigDecimal subTotal = BigDecimal.ZERO;
        for (CartOrderItemProjection item : items) {
            if (item.getQuantity() > item.getStockQuantity()) {
                log.warn(
                        "Create order rejected: insufficient stock. userId={}, productId={}, variantId={}, " +
                                "productName={}, requestedQuantity={}, availableStock={}",
                        userId, item.getProductId(), item.getProductVariantId(), item.getProductName(),
                        item.getQuantity(), item.getStockQuantity());

                throw new BadRequestException("Insufficient stock for product: " + item.getProductName());
            }

            BigDecimal itemTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            subTotal = subTotal.add(itemTotal);
        }

        Coupon coupon = null;
        BigDecimal discountAmount = BigDecimal.ZERO;

        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            String couponCode = request.couponCode().trim();

            coupon = couponRepository.findByCodeIgnoreCaseAndActiveTrue(couponCode).orElseThrow(() -> {
                log.warn("Create order rejected: coupon not found or inactive. couponCode={}, userId={}",
                        couponCode, userId);
                return new ResourceNotFoundException("Coupon not found or inactive");
            });

            Instant now = Instant.now();

            if (now.isBefore(coupon.getValidFrom()) || now.isAfter(coupon.getValidUntil())) {
                log.warn("Apply coupon rejected: coupon is outside validity period. couponCode={}, userId={}, validFrom={}, validUntil={}",
                        coupon.getCode(), userId, coupon.getValidFrom(), coupon.getValidUntil());
                throw new BadRequestException("Coupon is expired or not yet active");
            }

            if (coupon.getUsageLimit() != null && coupon.getUsedCount() >= coupon.getUsageLimit()) {
                log.warn("Apply coupon rejected: usage limit reached. couponCode={}, userId={}, usedCount={}, usageLimit={}",
                        coupon.getCode(), userId, coupon.getUsedCount(), coupon.getUsageLimit());
                throw new BadRequestException("Coupon usage limit has been reached");
            }

            if (coupon.getMinimumOrderAmount() != null && subTotal.compareTo(coupon.getMinimumOrderAmount()) < 0) {
                log.warn("Apply coupon rejected: minimum order amount not met. couponCode={}, userId={}, subtotal={}, minimumOrderAmount={}",
                        coupon.getCode(), userId, subTotal, coupon.getMinimumOrderAmount());
                throw new BadRequestException("Minimum order amount for this coupon is " + coupon.getMinimumOrderAmount());
            }

            if (coupon.getDiscountType() == DiscountType.PERCENTAGE) {
                discountAmount = subTotal.multiply(coupon.getDiscountValue()).divide(BigDecimal.valueOf(100));

                if (coupon.getMaximumDiscountAmount() != null && discountAmount.compareTo(coupon.getMaximumDiscountAmount()) > 0) {
                    discountAmount = coupon.getMaximumDiscountAmount();
                }
            } else {
                discountAmount = coupon.getDiscountValue();

                if (discountAmount.compareTo(subTotal) > 0) {
                    discountAmount = subTotal;
                }
            }
        }

        BigDecimal shippingAmount = new BigDecimal("40");

        BigDecimal taxableAmount = subTotal.subtract(discountAmount);
        BigDecimal taxAmount = taxableAmount.multiply(new BigDecimal("0.18")).setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalAmount = taxableAmount.add(shippingAmount).add(taxAmount);

        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .userId(userId)
                .shippingAddressId(address.getId())
                .totalAmount(totalAmount)
                .shippingAmount(shippingAmount)
                .discountAmount(discountAmount)
                .taxAmount(taxAmount)
                .couponId(coupon != null ? coupon.getId() : null)
                .couponCode(coupon != null ? coupon.getCode() : null)
                .paymentStatus(PaymentStatus.PENDING)
                .orderStatus(OrderStatus.PENDING).build();

        order = orderRepository.save(order);

        List<OrderItem> orderItems = new ArrayList<>();

        for (CartOrderItemProjection item : items) {
            BigDecimal itemTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));

            OrderItem orderItem = OrderItem.builder()
                    .orderId(order.getId())
                    .productId(item.getProductId())
                    .productVariantId(item.getProductVariantId())
                    .productName(item.getProductName())
                    .variantName(item.getSku())
                    .quantity(item.getQuantity())
                    .unitPrice(item.getUnitPrice())
                    .totalPrice(itemTotal)
                    .build();

            orderItems.add(orderItem);
        }

        orderItemRepository.saveAll(orderItems);

        Payment payment = Payment.builder()
                .orderId(order.getId())
                .paymentMethod(request.paymentMethod())
                .amount(totalAmount)
                .paymentStatus(PaymentStatus.PENDING)
                .build();

        payment = paymentRepository.save(payment);

        if (request.paymentMethod() == PaymentMethod.COD) {
            orderFinalizationService.finalizeOrder(order);

            log.info("COD order finalized successfully. orderId={}, orderNumber={}", order.getId(), order.getOrderNumber());
        }

        PaymentResponse paymentResponse = objectMapper.convertValue(payment, PaymentResponse.class);

        log.info("Order created successfully. orderId={}, orderNumber={}, userId={}, paymentMethod={}, subtotal={}, " +
                        "discount={}, shipping={}, tax={}, total={}",
                order.getId(), order.getOrderNumber(), userId, request.paymentMethod(), subTotal, discountAmount,
                shippingAmount, taxAmount, totalAmount);

        List<OrderItemEmailData> orderItemEmailData = orderItems.stream().map(orderItem ->
                objectMapper.convertValue(orderItem, OrderItemEmailData.class)).toList();

        sendOrderPlacedMail(order, user.getFullName(), orderItemEmailData, address.getFullAddress(), user.getEmail());

        return OrderResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .subtotal(subTotal)
                .shippingAmount(order.getShippingAmount())
                .discountAmount(order.getDiscountAmount())
                .taxAmount(order.getTaxAmount())
                .totalAmount(order.getTotalAmount())
                .couponId(order.getCouponId())
                .couponCode(order.getCouponCode())
                .paymentStatus(order.getPaymentStatus())
                .orderStatus(order.getOrderStatus())
                .payment(paymentResponse)
                .build();
    }

    private String generateOrderNumber() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

        String random = UUID.randomUUID()
                .toString()
                .substring(0, 6)
                .toUpperCase();

        return "ORD-" + date + "-" + random;
    }

    public PageResponse<OrderListResponse> getOrders(Pageable pageable, Authentication authentication) {
        UUID userId = ((User) authentication.getPrincipal()).getId();
        User user = userRepository.findById(userId).orElseThrow(() -> {
            log.warn("get orders failed: user not found, userId={}", userId);
            return new ResourceNotFoundException("User not found");
        });

        Page<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable);

        Page<OrderListResponse> orderResponses = orders.map(order ->
                objectMapper.convertValue(order, OrderListResponse.class));

        return new PageResponse<>(
                orderResponses.getContent(),
                orderResponses.getNumber(),
                orderResponses.getSize(),
                orderResponses.getTotalElements(),
                orderResponses.getTotalPages(),
                orderResponses.isFirst(),
                orderResponses.isLast()
        );
    }

    public OrderDetailResponse getOrderDetail(UUID orderId, Authentication authentication) {
        UUID userId = ((User) authentication.getPrincipal()).getId();
        User user = userRepository.findById(userId).orElseThrow(() -> {
            log.warn("Get order details failed: user not found, userId={}", userId);
            return new ResourceNotFoundException("User not found");
        });

        Order order = orderRepository.findByIdAndUserId(orderId, user.getId()).orElseThrow(() -> {
            log.warn("Get order details failed: order not found, orderId={}, userId={}", orderId, userId);
            return new ResourceNotFoundException("Order not found");
        });

        List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());

        List<OrderItemResponse> orderItemResponses = orderItems.stream().map(orderItem -> {
            String imageUrl = productVariantImageRepository.findByProductVariantIdAndPrimaryTrue(orderItem.getProductVariantId())
                    .map(ProductVariantImage::getImageUrl).orElse(null);

            return new OrderItemResponse(
                    orderItem.getId(),
                    orderItem.getOrderId(),
                    orderItem.getProductId(),
                    orderItem.getProductName(),
                    imageUrl,
                    orderItem.getProductVariantId(),
                    orderItem.getVariantName(),
                    orderItem.getQuantity(),
                    orderItem.getUnitPrice(),
                    orderItem.getTotalPrice(),
                    orderItem.getCreatedAt()
            );
        }).toList();

        Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow(() -> {
            log.warn("Get order details failed: payment not found. orderId={}, userId={}", orderId, userId);
            return new ResourceNotFoundException("Payment not found");
        });
        PaymentResponse paymentResponse = objectMapper.convertValue(payment, PaymentResponse.class);

        Shipment shipment = shipmentRepository.findByOrderId(order.getId()).orElse(null);

        UserShipmentResponse shipmentResponse = null;

        if (shipment != null) {
            List<ShipmentTrackingEvent> shipmentTrackingEvents = shipmentTrackingEventRepository
                    .findByShipmentIdOrderByEventTimeDesc(shipment.getId());

            List<ShipmentTimelineResponse> shipmentTimelineResponses = shipmentTrackingEvents.stream().map(shipmentTrackingEvent ->
                    objectMapper.convertValue(shipmentTrackingEvent, ShipmentTimelineResponse.class)).toList();

            shipmentResponse = UserShipmentResponse.builder()
                    .shipmentId(shipment.getId())
                    .courierName(shipment.getCourierName())
                    .trackingNumber(shipment.getTrackingNumber())
                    .shipmentStatus(shipment.getShipmentStatus())
                    .shippedAt(shipment.getShippedAt())
                    .deliveredAt(shipment.getDeliveredAt())
                    .timeline(shipmentTimelineResponses).build();
        }

        UserAddress shippingAddress = userAddressRepository.findByUserIdAndId(userId, order.getShippingAddressId())
                .orElseThrow(() -> {
                    log.warn("Get order details failed: shipping address not found. orderId={}, userId={}, addressId={}",
                            orderId, userId, order.getShippingAddressId());
                    return new ResourceNotFoundException("Shipping address not found");
                });

        AddressResponse shippingAddressResponse = objectMapper.convertValue(shippingAddress, AddressResponse.class);

        return OrderDetailResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .subtotal(order.getTotalAmount()
                        .subtract(order.getShippingAmount())
                        .subtract(order.getTaxAmount())
                        .add(order.getDiscountAmount())
                )
                .shippingAmount(order.getShippingAmount())
                .discountAmount(order.getDiscountAmount())
                .taxAmount(order.getTaxAmount())
                .totalAmount(order.getTotalAmount())
                .paymentStatus(order.getPaymentStatus())
                .orderStatus(order.getOrderStatus())
                .items(orderItemResponses)
                .payment(paymentResponse)
                .shippingAddress(shippingAddressResponse)
                .userShipmentResponse(shipmentResponse)
                .build();
    }

    @Transactional
    public OrderResponse cancelOrder(UUID orderId, Authentication authentication) {
        UUID userId = ((User) authentication.getPrincipal()).getId();
        User user = userRepository.findById(userId).orElseThrow(() -> {
            log.warn("Cancel order failed: user not found. orderId={}, userId={}", orderId, userId);
            return new ResourceNotFoundException("User not found");
        });

        Order order = orderRepository.findByIdAndUserId(orderId, userId).orElseThrow(() -> {
            log.warn("Cancel order failed: order not found. orderId={}, userId={}", orderId, userId);
            return new ResourceNotFoundException("Order not found");
        });

        if (order.getOrderStatus() != OrderStatus.PENDING && order.getOrderStatus() != OrderStatus.CONFIRMED) {
            throw new BadRequestException("Order cannot be cancelled at this stage");
        }

        Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow(() ->
                new ResourceNotFoundException("Payment not found")
        );

        if (payment.getPaymentMethod() == PaymentMethod.RAZORPAY && payment.getPaymentStatus() == PaymentStatus.CAPTURED) {
            log.info("Refunding captured payment for cancelled order. orderId={}, paymentId={}",
                    orderId, payment.getId());
            paymentService.refundPayment(payment);
        }

        int updatedRows = productVariantRepository.restoreStock(order.getId());

        if (updatedRows == 0) {
            log.warn("Stock restoration failed: no stock rows updated. orderId={}", order.getId());
            throw new BadRequestException("Unable to restore stock");
        }

        order.setOrderStatus(OrderStatus.CANCELLED);

        orderRepository.save(order);

        sendOrderCancelledMail(user.getFullName(), order.getOrderNumber(), order.getTotalAmount(), Instant.now(), user.getEmail());

        return objectMapper.convertValue(order, OrderResponse.class);
    }

    public void sendOrderPlacedMail(
            Order order, String fullName, List<OrderItemEmailData> items, String shippingAddress, String recipientEmail
    ) {
        Map<String, Object> data = new HashMap<>();
        data.put("customerName", fullName);
        data.put("orderNumber", order.getOrderNumber());
        data.put("placedAt", order.getCreatedAt());
        data.put("items", items);
        data.put("subtotal", getSubTotal(order));
        data.put("discountAmount", order.getDiscountAmount().setScale(2, RoundingMode.HALF_UP));
        data.put("shippingAmount", order.getShippingAmount().setScale(2, RoundingMode.HALF_UP));
        data.put("taxAmount", order.getTaxAmount().setScale(2, RoundingMode.HALF_UP));
        data.put("totalAmount", order.getTotalAmount().setScale(2, RoundingMode.HALF_UP));

        data.put("paymentStatus", order.getPaymentStatus());
        data.put("shippingAddress", shippingAddress);

        NotificationRequest request = NotificationRequest.builder()
                .channel(NotificationChannel.EMAIL)
                .event(NotificationEvent.ORDER_PLACED)
                .recipient(recipientEmail)
                .data(data)
                .build();

        notificationService.send(request);
    }

    public void sendOrderCancelledMail(
            String fullName, String orderNumber, BigDecimal amount, Instant cancelledAt, String recipientEmail
    ) {
        Map<String, Object> data = new HashMap<>();
        data.put("customerName", fullName);
        data.put("orderNumber", orderNumber);
        data.put("amount", amount);
        data.put("cancellationReason", "Order cancelled by customer");
        data.put("paidAt", DateTimeUtil.format(cancelledAt));

        NotificationRequest request = NotificationRequest.builder()
                .channel(NotificationChannel.EMAIL)
                .event(NotificationEvent.ORDER_CANCELLED)
                .recipient(recipientEmail)
                .data(data)
                .build();

        notificationService.send(request);
    }

    public BigDecimal getSubTotal(Order order) {
        return order.getTotalAmount()
                .subtract(order.getDiscountAmount())
                .subtract(order.getShippingAmount())
                .subtract(order.getTaxAmount())
                .setScale(2, RoundingMode.HALF_UP);
    }
}
