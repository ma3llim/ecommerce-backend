package org.ecommerce.user.repository;

import org.ecommerce.user.entity.ContactMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, UUID> {
}
