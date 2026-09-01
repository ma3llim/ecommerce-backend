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
@Table(name = "contact_messages")
@EntityListeners(AuditingEntityListener.class)
public class ContactMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @CreatedDate
    private Instant createdAt;
}
