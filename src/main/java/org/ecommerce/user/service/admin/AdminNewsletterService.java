package org.ecommerce.user.service.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ecommerce.common.dtos.PageResponse;
import org.ecommerce.user.dtos.admin.response.NewsletterResponse;
import org.ecommerce.user.entity.NewsletterSubscriber;
import org.ecommerce.user.repository.NewsletterSubscriberRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminNewsletterService {
    private final NewsletterSubscriberRepository newsletterSubscriberRepository;

    public PageResponse<NewsletterResponse> getAllSubscribers(Pageable pageable) {

        Page<NewsletterSubscriber> subscribers = newsletterSubscriberRepository.findAll(pageable);

        Page<NewsletterResponse> response = subscribers.map(
                subscriber -> new NewsletterResponse(
                        subscriber.getId(),
                        subscriber.getEmail(),
                        subscriber.getCreatedAt()
                )
        );

        log.info("Newsletter subscribers retrieved successfully: page={}, size={}, totalElements={}",
                subscribers.getNumber(),
                subscribers.getSize(),
                subscribers.getTotalElements()
        );

        return new PageResponse<>(
                response.getContent(),
                response.getNumber(),
                response.getSize(),
                response.getTotalElements(),
                response.getTotalPages(),
                response.isFirst(),
                response.isLast()
        );
    }
}
