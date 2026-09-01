package org.ecommerce.user.service.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ecommerce.common.dtos.PageResponse;
import org.ecommerce.common.exception.ResourceNotFoundException;
import org.ecommerce.user.dtos.admin.response.ContactDetailsResponse;
import org.ecommerce.user.dtos.admin.response.ContactListResponse;
import org.ecommerce.user.entity.ContactMessage;
import org.ecommerce.user.repository.ContactMessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;


@Service
@RequiredArgsConstructor
@Slf4j
public class AdminContactService {
    private final ContactMessageRepository contactMessageRepository;

    public PageResponse<ContactListResponse> getAllContacts(Pageable pageable) {
        Page<ContactMessage> contacts = contactMessageRepository.findAll(pageable);

        Page<ContactListResponse> response = contacts.map(contact -> new ContactListResponse(
                        contact.getId(),
                        contact.getFirstName(),
                        contact.getLastName(),
                        contact.getEmail(),
                        contact.getSubject(),
                        contact.getCreatedAt()
                )
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

    public ContactDetailsResponse getContactById(UUID contactId) {
        ContactMessage contact = contactMessageRepository.findById(contactId).orElseThrow(() -> {
            log.warn("Fetch contact message failed: contact not found, contactId={}", contactId);
            return new ResourceNotFoundException("Contact message not found");
        });

        return new ContactDetailsResponse(
                contact.getId(),
                contact.getFirstName(),
                contact.getLastName(),
                contact.getEmail(),
                contact.getSubject(),
                contact.getMessage(),
                contact.getCreatedAt()
        );
    }
}
