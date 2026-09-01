package org.ecommerce.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Refund;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ecommerce.auth.entities.User;
import org.ecommerce.auth.repository.UserRepository;
import org.ecommerce.common.exception.BadRequestException;
import org.ecommerce.common.exception.ExternalServiceException;
import org.ecommerce.common.exception.ResourceNotFoundException;
import org.ecommerce.common.notification.dtos.NotificationRequest;
import org.ecommerce.common.notification.enums.channel.NotificationChannel;
import org.ecommerce.common.notification.enums.channel.NotificationEvent;
import org.ecommerce.common.notification.service.NotificationService;
import org.ecommerce.common.utils.DateTimeUtil;
import org.ecommerce.order.config.RazorpayProperties;
import org.ecommerce.order.dtos.response.PaymentResponse;
import org.ecommerce.order.entities.Order;
import org.ecommerce.order.entities.Payment;
import org.ecommerce.order.enums.OrderStatus;
import org.ecommerce.order.enums.PaymentMethod;
import org.ecommerce.order.enums.PaymentStatus;
import org.ecommerce.order.repository.OrderItemRepository;
import org.ecommerce.order.repository.OrderRepository;
import org.ecommerce.order.repository.PaymentRepository;
import org.json.JSONObject;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {
    private final RazorpayClient razorpayClient;
    private final RazorpayProperties razorpayProperties;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderFinalizationService orderFinalizationService;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public String createRazorpayOrder(UUID orderId, BigDecimal amount) {
        JSONObject options = new JSONObject();

        long amountInPaise = amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();

        options.put("amount", amountInPaise);
        options.put("currency", razorpayProperties.currency());
        options.put("receipt", orderId.toString());

        try {
            com.razorpay.Order razorpayOrder = razorpayClient.orders.create(options);
            return razorpayOrder.get("id");
        } catch (RazorpayException e) {
            log.error("Razorpay order creation failed. orderId={}, amount={}, currency={}",
                    orderId, amount, razorpayProperties.currency(), e);
            throw new ExternalServiceException("Unable to create Razorpay order");
        }
    }

    @Transactional
    public void handleWebhook(String payload, String signature) {
        try {
            Utils.verifyWebhookSignature(payload, signature, razorpayProperties.webhookSecret());
        } catch (RazorpayException e) {
            log.warn("Invalid Razorpay webhook signature");
            throw new ExternalServiceException("Invalid webhook signature");
        }

        JSONObject webhook = new JSONObject(payload);

        String event = webhook.getString("event");

        if (!event.equals("payment.captured") && !event.equals("payment.failed")) {
            log.info("Ignoring unsupported Razorpay event: {}", event);
            return;
        }

        // Razorpay order ID
        JSONObject paymentEntity = webhook.getJSONObject("payload").getJSONObject("payment")
                .getJSONObject("entity");

        String razorpayPaymentId = paymentEntity.getString("id");
        String razorpayOrderId = paymentEntity.getString("order_id");
        long razorpayAmount = paymentEntity.getLong("amount");
        String razorpayCurrency = paymentEntity.getString("currency");

        Payment payment = paymentRepository.findByRazorpayOrderId(razorpayOrderId).orElseThrow(() -> {
            log.warn("Razorpay webhook rejected: payment not found. razorpayOrderId={}, event={}",
                    razorpayOrderId, event);
            return new ResourceNotFoundException("Payment not found");
        });

        if (payment.getPaymentStatus() == PaymentStatus.CAPTURED || payment.getPaymentStatus() == PaymentStatus.FAILED
                || payment.getPaymentStatus() == PaymentStatus.REFUNDED) {
            log.info(
                    "Payment webhook already processed. paymentId={}, status={}, event={}",
                    payment.getId(),
                    payment.getPaymentStatus(),
                    event
            );
            return;
        }
        long expectedAmount = payment.getAmount().movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();

        if (razorpayAmount != expectedAmount) {
            log.error("Razorpay webhook rejected: amount mismatch. paymentId={}, expectedAmount={}, receivedAmount={}",
                    payment.getId(),
                    expectedAmount,
                    razorpayAmount
            );
            throw new BadRequestException("Payment amount mismatch");
        }

        if (!razorpayCurrency.equalsIgnoreCase(payment.getCurrency())) {
            log.error("Razorpay webhook rejected: currency mismatch. paymentId={}, expectedCurrency={}, receivedCurrency={}",
                    payment.getId(),
                    payment.getCurrency(),
                    razorpayCurrency
            );
            throw new BadRequestException("Payment currency mismatch");
        }

        if (event.equals("payment.captured")) {
            payment.setTransactionId(razorpayPaymentId);
            payment.setPaymentStatus(PaymentStatus.CAPTURED);

            paymentRepository.save(payment);

            Order order = orderRepository.findById(payment.getOrderId()).orElseThrow(() -> {
                log.warn("Razorpay webhook processing failed: order not found. paymentId={}, orderId={}",
                        payment.getId(), payment.getOrderId());
                return new ResourceNotFoundException("Order not found");
            });

            User user = userRepository.findById(order.getUserId()).orElseThrow(() -> {
                log.warn("Payment success email failed: user not found. orderId={}, userId={}",
                        order.getId(), order.getUserId());
                return new ResourceNotFoundException("User not found");
            });

            order.setPaymentStatus(PaymentStatus.SUCCESS);
            orderRepository.save(order);

            orderFinalizationService.finalizeOrder(order);

            sendPaymentSuccessMail(
                    user.getFullName(),
                    order.getOrderNumber(),
                    razorpayPaymentId,
                    payment.getAmount(),
                    payment.getPaymentMethod(),
                    Instant.now(),
                    user.getEmail()
            );
        } else {
            payment.setPaymentStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);

            Order order = orderRepository.findById(payment.getOrderId()).orElseThrow(() -> {
                log.warn("Payment failure email failed: order not found. paymentId={}, orderId={}",
                        payment.getId(), payment.getOrderId());
                return new ResourceNotFoundException("Order not found");
            });

            User user = userRepository.findById(order.getUserId()).orElseThrow(() -> {
                log.warn("Payment failure email failed: user not found. orderId={}, userId={}",
                        order.getId(), order.getUserId());
                return new ResourceNotFoundException("User not found");
            });

            sendPaymentFailedMail(user.getFullName(), order.getOrderNumber(),
                    "Payment was declined or could not be completed",
                    payment.getAmount(),
                    Instant.now(),
                    user.getEmail()
            );
        }
        log.info("Razorpay webhook processed successfully. event={}, razorpayOrderId={}", event, razorpayOrderId);
    }

    public void refundPayment(Payment payment) {
        if (payment.getTransactionId() == null) {
            log.warn("Payment refund rejected: transaction ID not found. paymentId={}", payment.getId());
            throw new BadRequestException("Cannot refund payment: transaction ID not found");
        }

        if (payment.getPaymentStatus() != PaymentStatus.CAPTURED) {
            log.warn("Payment refund rejected: payment is not captured. paymentId={}, status={}",
                    payment.getId(), payment.getPaymentStatus());
            throw new BadRequestException("Payment is not eligible for refund");
        }

        Order order = orderRepository.findById(payment.getOrderId()).orElseThrow(() -> {
            log.warn("Payment refund rejected: order is not found. orderId:{}", payment.getOrderId());
            return new BadRequestException("Order is not found");
        });

        User user = userRepository.findById(order.getUserId()).orElseThrow(() -> {
            log.warn("Payment refund rejected: user is not found. orderId:{}, userId: {}",
                    payment.getOrderId(), order.getUserId());
            return new BadRequestException("Order is not found");
        });

        try {
            Refund refund = razorpayClient.payments.refund(payment.getTransactionId());
            String refundId = refund.get("id");
            log.info("Razorpay refund created successfully. paymentId={}, refundId={}",
                    payment.getTransactionId(), refundId);

            Instant now = Instant.now();

            payment.setPaymentStatus(PaymentStatus.REFUNDED);
            payment.setRefundId(refundId);
            payment.setRefundAt(now);

            paymentRepository.save(payment);

            sendRefundedMail(user.getFullName(), order.getOrderNumber(), refundId, order.getTotalAmount(), now, user.getEmail());
        } catch (RazorpayException e) {
            log.error("Razorpay refund failed. transactionId={}, paymentId={}",
                    payment.getTransactionId(), payment.getId(), e);
            throw new ExternalServiceException("Unable to process payment refund");
        }
    }

    public PaymentResponse initiatePayment(UUID orderId, Authentication authentication) {
        UUID userId = ((User) authentication.getPrincipal()).getId();

        Order order = orderRepository.findById(orderId).orElseThrow(() -> {
            log.warn("Payment initiation failed: order not found, orderId={}", orderId);
            return new ResourceNotFoundException("Order not found");
        });

        if (!order.getUserId().equals(userId)) {
            log.warn("Unauthorized payment attempt: orderId={}, userId={}", orderId, userId);
            throw new BadRequestException("You are not allowed to pay for this order");
        }

        if (order.getPaymentStatus() == PaymentStatus.SUCCESS) {
            log.warn("Payment initiation rejected: order already paid. orderId={}, userId={}", orderId, userId);
            throw new BadRequestException("Order has already been paid");
        }

        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            log.warn("Payment initiation rejected: order is cancelled. orderId={}, userId={}", orderId, userId);
            throw new BadRequestException("Cancelled order cannot be paid");
        }

        if (order.getOrderStatus() == OrderStatus.DELIVERED) {
            log.warn("Payment initiation rejected: order is already delivered. orderId={}, userId={}", orderId, userId);
            throw new BadRequestException("Delivered order cannot be paid");
        }

        Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow(() -> {
            log.warn("Payment record not found: orderId={}", orderId);
            return new ResourceNotFoundException("Payment record not found");
        });

        if (payment.getPaymentMethod() == PaymentMethod.COD) {
            log.warn("Payment initiation rejected: order uses COD. orderId={}, userId={}", orderId, userId);
            throw new BadRequestException("COD payment does not require online payment");
        }

        if (payment.getRazorpayOrderId() != null) {
            log.info("Existing Razorpay order found: orderId={}, razorpayOrderId={}",
                    orderId, payment.getRazorpayOrderId());

            return objectMapper.convertValue(payment, PaymentResponse.class);
        }

        BigDecimal amount = order.getTotalAmount();

        String razorpayOrderId = createRazorpayOrder(order.getId(), amount);

        payment.setRazorpayOrderId(razorpayOrderId);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setCurrency(razorpayProperties.currency());

        paymentRepository.save(payment);

        log.info("Razorpay payment initiated: orderId={}, razorpayOrderId={}, amount={}", orderId, razorpayOrderId, amount);

        return objectMapper.convertValue(payment, PaymentResponse.class);
    }

    public boolean hasPurchasedVariant(UUID userId, UUID productId, UUID productVariantId) {
        return orderItemRepository.existsPurchasedProductVariant(
                userId,
                OrderStatus.DELIVERED,
                productId,
                productVariantId
        );
    }

    public void sendPaymentSuccessMail(
            String fullName, String orderNumber, String paymentId, BigDecimal amount, PaymentMethod paymentMethod,
            Instant paidAt, String recipientEmail
    ) {
        Map<String, Object> data = new HashMap<>();
        data.put("customerName", fullName);
        data.put("orderNumber", orderNumber);
        data.put("paymentId", paymentId);
        data.put("amount", amount);
        data.put("paymentMethod", paymentMethod);
        data.put("paidAt", DateTimeUtil.format(paidAt));

        NotificationRequest request = NotificationRequest.builder()
                .channel(NotificationChannel.EMAIL)
                .event(NotificationEvent.PAYMENT_SUCCESS)
                .recipient(recipientEmail)
                .data(data)
                .build();

        notificationService.send(request);
    }

    public void sendPaymentFailedMail(
            String fullName, String orderNumber, String failureReason, BigDecimal amount,
            Instant failedAt, String recipientEmail
    ) {
        Map<String, Object> data = new HashMap<>();
        data.put("customerName", fullName);
        data.put("orderNumber", orderNumber);
        data.put("amount", amount);
        data.put("failureReason", failureReason);
        data.put("failedAt", DateTimeUtil.format(failedAt));

        NotificationRequest request = NotificationRequest.builder()
                .channel(NotificationChannel.EMAIL)
                .event(NotificationEvent.PAYMENT_FAILED)
                .recipient(recipientEmail)
                .data(data)
                .build();

        notificationService.send(request);
    }

    public void sendRefundedMail(
            String fullName, String orderNumber, String refundId, BigDecimal refundAmount,
            Instant refundedAt, String recipientEmail
    ) {
        Map<String, Object> data = new HashMap<>();
        data.put("customerName", fullName);
        data.put("orderNumber", orderNumber);
        data.put("refundId", refundId);
        data.put("refundAmount", refundAmount);
        data.put("refundedAt", DateTimeUtil.format(refundedAt));

        NotificationRequest request = NotificationRequest.builder()
                .channel(NotificationChannel.EMAIL)
                .event(NotificationEvent.REFUND_COMPLETED)
                .recipient(recipientEmail)
                .data(data)
                .build();

        notificationService.send(request);
    }
}
