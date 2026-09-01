package org.ecommerce.user.service.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ecommerce.common.exception.ResourceAlreadyExistsException;
import org.ecommerce.user.dtos.user.request.NewsletterRequest;
import org.ecommerce.user.entity.NewsletterSubscriber;
import org.ecommerce.user.repository.NewsletterSubscriberRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsletterService {
    private final NewsletterSubscriberRepository newsletterSubscriberRepository;

    public void subscribe(NewsletterRequest request) {
        String email = request.email().trim().toLowerCase();

        NewsletterSubscriber subscriber = NewsletterSubscriber.builder().email(email).build();

        try {
            NewsletterSubscriber savedSubscriber = newsletterSubscriberRepository.save(subscriber);

            log.info("Newsletter subscription created successfully: subscriberId={}, email={}",
                    savedSubscriber.getId(),
                    savedSubscriber.getEmail()
            );

        } catch (DataIntegrityViolationException exception) {
            log.warn("Newsletter subscription rejected: email already exists, email={}", email);
            throw new ResourceAlreadyExistsException("Email is already subscribed");
        }
    }
}
