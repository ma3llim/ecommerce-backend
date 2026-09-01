package org.ecommerce.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "newsletter_subscribers")
@EntityListeners(AuditingEntityListener.class)
public class NewsletterSubscriber {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
