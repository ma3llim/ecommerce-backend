package org.ecommerce.user.repository;

import org.ecommerce.user.entity.NewsletterSubscriber;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NewsletterSubscriberRepository extends JpaRepository<NewsletterSubscriber, UUID> {
}