package org.ecommerce.user.service.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ecommerce.user.dtos.user.request.ContactRequest;
import org.ecommerce.user.entity.ContactMessage;
import org.ecommerce.user.repository.ContactMessageRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContactService {
    private final ContactMessageRepository contactMessageRepository;

    public void createContactMessage(ContactRequest request) {
        ContactMessage contactMessage = ContactMessage.builder()
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .email(request.email().trim().toLowerCase())
                .subject(request.subject().trim())
                .message(request.message().trim())
                .build();

        ContactMessage savedContact = contactMessageRepository.save(contactMessage);

        log.info("Contact message created successfully: contactId={}, email={}",
                savedContact.getId(), savedContact.getEmail());
    }
}
