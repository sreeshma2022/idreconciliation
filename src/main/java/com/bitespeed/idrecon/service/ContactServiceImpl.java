package com.bitespeed.idrecon.service;

import com.bitespeed.idrecon.dto.ContactResponse;
import com.bitespeed.idrecon.entity.Contact;
import com.bitespeed.idrecon.repository.IContactRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional
public class ContactServiceImpl implements IContactService {

    private final IContactRepository contactRepository;


    @Override
    public ContactResponse identifyContact(String email, String phoneNumber) {

        // Step 1: Fetch contacts with either email or phone number
        List<Contact> initialContacts = contactRepository.findByEmailOrPhoneNumber(email, phoneNumber);

        // Step 2: If no existing contacts, create a new primary contact
        if (initialContacts.isEmpty()) {
            Contact newContact = Contact.builder()
                    .email(email)
                    .phoneNumber(phoneNumber)
                    .linkPrecedence("primary")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            contactRepository.save(newContact);
            return buildContactResponse(newContact, Collections.emptyList());
        }

        // Step 3: Identify primary contact
        Contact primaryContact = initialContacts.stream()
                .filter(c -> "primary".equals(c.getLinkPrecedence()))
                .min(Comparator.comparing(Contact::getCreatedAt))
                .orElse(initialContacts.get(0));

        // Step 4: Get all linked/transitive contacts in one fetch
        Set<Long> relatedIds = initialContacts.stream()
                .flatMap(c -> Stream.of(c.getId(), c.getLinkedId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        relatedIds.add(primaryContact.getId()); // Ensure primary ID is included

        List<Contact> allLinked = new ArrayList<>(contactRepository.findAllByLinkedIdInOrIdIn(relatedIds, relatedIds));
        allLinked = new ArrayList<>(new HashSet<>(allLinked)); // Deduplicate

        // Step 5: Normalize link precedence (batch update)
        List<Contact> toUpdate = new ArrayList<>();

        for (Contact contact : allLinked) {
            if (!Objects.equals(contact.getId(), primaryContact.getId())) {
                boolean updated = false;
                if ("primary".equals(contact.getLinkPrecedence())) {
                    contact.setLinkPrecedence("secondary");
                    updated = true;
                }
                if (contact.getLinkedId() == null || !contact.getLinkedId().equals(primaryContact.getId())) {
                    contact.setLinkedId(primaryContact.getId());
                    updated = true;
                }
                if (updated) {
                    contact.setUpdatedAt(LocalDateTime.now());
                    toUpdate.add(contact);
                }
            }
        }

        if (!toUpdate.isEmpty()) {
            contactRepository.saveAll(toUpdate);
        }

        // Step 6: Add new secondary contact if needed (no re-fetch)
        boolean emailExists = allLinked.stream().anyMatch(c -> email != null && email.equals(c.getEmail()));
        boolean phoneExists = allLinked.stream().anyMatch(c -> phoneNumber != null && phoneNumber.equals(c.getPhoneNumber()));

        if (!emailExists || !phoneExists) {
            Contact newContact = Contact.builder()
                    .email(email)
                    .phoneNumber(phoneNumber)
                    .linkedId(primaryContact.getId())
                    .linkPrecedence("secondary")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            contactRepository.save(newContact);
            allLinked.add(newContact); // no need to re-fetch
        }

        // Step 7: Build response
        return buildContactResponse(primaryContact, allLinked);
    }


    private Contact createSecondaryIfNewInfo(String email, String phoneNumber, List<Contact> allLinked, Contact primaryContact) {
        boolean emailExists = allLinked.stream().anyMatch(c -> email != null && email.equals(c.getEmail()));
        boolean phoneExists = allLinked.stream().anyMatch(c -> phoneNumber != null && phoneNumber.equals(c.getPhoneNumber()));

        if (!emailExists || !phoneExists) {
            Contact newContact = Contact.builder()
                    .email(email)
                    .phoneNumber(phoneNumber)
                    .linkedId(primaryContact.getId())
                    .linkPrecedence("secondary")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            return contactRepository.save(newContact);
        }
        return null;
    }

    private ContactResponse buildContactResponse(Contact primaryContact, List<Contact> contacts) {
        Set<String> emails = new LinkedHashSet<>();
        Set<String> phoneNumbers = new LinkedHashSet<>();
        List<Long> secondaryIds = new ArrayList<>();

        if (primaryContact.getEmail() != null) emails.add(primaryContact.getEmail());
        if (primaryContact.getPhoneNumber() != null) phoneNumbers.add(primaryContact.getPhoneNumber());

        for (Contact contact : contacts) {
            if (!Objects.equals(contact.getId(), primaryContact.getId())) {
                if (contact.getEmail() != null) emails.add(contact.getEmail());
                if (contact.getPhoneNumber() != null) phoneNumbers.add(contact.getPhoneNumber());
                secondaryIds.add(contact.getId());
            }
        }

        return new ContactResponse(
                primaryContact.getId(),
                new ArrayList<>(emails),
                new ArrayList<>(phoneNumbers),
                secondaryIds
        );
    }
}
