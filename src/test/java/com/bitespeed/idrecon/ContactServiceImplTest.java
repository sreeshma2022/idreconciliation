package com.bitespeed.idrecon;

import com.bitespeed.idrecon.dto.ContactResponse;
import com.bitespeed.idrecon.entity.Contact;
import com.bitespeed.idrecon.repository.IContactRepository;
import com.bitespeed.idrecon.service.ContactServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ContactServiceImplTest {

    @InjectMocks
    private ContactServiceImpl contactService;

    @Mock
    private IContactRepository contactRepository;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    private Contact createContact(Long id, String email, String phone, String precedence, Long linkedId) {
        return Contact.builder()
                .id(id)
                .email(email)
                .phoneNumber(phone)
                .linkPrecedence(precedence)
                .linkedId(linkedId)
                .createdAt(LocalDateTime.now().minusDays(id)) // older contact has lower id
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    public void testCreateNewPrimaryContact_WhenNoExistingContact() {
        when(contactRepository.findByEmailOrPhoneNumber("test@example.com", "9999999999"))
                .thenReturn(Collections.emptyList());

        ContactResponse response = contactService.identifyContact("test@example.com", "9999999999");

        assertNotNull(response);
        assertEquals(1, response.getEmails().size());
        assertEquals("test@example.com", response.getEmails().get(0));
        assertEquals("9999999999", response.getPhoneNumbers().get(0));
        assertTrue(response.getSecondaryContactIds().isEmpty());

        verify(contactRepository, times(1)).save(any(Contact.class));
    }

    @Test
    public void testLinkToExistingPrimaryContact() {
        Contact primary = createContact(1L, "a@example.com", "123456", "primary", null);
        when(contactRepository.findByEmailOrPhoneNumber("b@example.com", "123456"))
                .thenReturn(List.of(primary));

        when(contactRepository.findAllByLinkedIdInOrIdIn(anySet(), anySet()))
                .thenReturn(Collections.emptyList());

        when(contactRepository.save(any(Contact.class))).thenAnswer(invocation -> {
            Contact saved = invocation.getArgument(0);
            saved.setId(2L); // Assign a new ID to the new secondary contact
            return saved;
        });

        when(contactRepository.findAllById(anySet()))
                .thenReturn(List.of(
                        primary,
                        Contact.builder()
                                .id(2L)
                                .email("b@example.com")
                                .phoneNumber("123456")
                                .linkPrecedence("secondary")
                                .linkedId(1L)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build()
                ));

        ContactResponse response = contactService.identifyContact("b@example.com", "123456");

        assertNotNull(response);
        assertEquals(Set.of("a@example.com", "b@example.com"), new HashSet<>(response.getEmails()));
        assertEquals(List.of("123456"), response.getPhoneNumbers());
        assertEquals(List.of(2L), response.getSecondaryContactIds());
    }



    @Test
    public void testPrimaryContactMergingKeepsOldestPrimary() {
        // Arrange: Two existing primary contacts
        Contact c1 = Contact.builder()
                .id(11L)
                .email("george@hillvalley.edu")
                .phoneNumber("919191")
                .linkPrecedence("primary")
                .linkedId(null)
                .createdAt(LocalDateTime.of(2023, 4, 11, 0, 0))
                .updatedAt(LocalDateTime.of(2023, 4, 11, 0, 0))
                .build();

        Contact c2 = Contact.builder()
                .id(27L)
                .email("biffsucks@hillvalley.edu")
                .phoneNumber("717171")
                .linkPrecedence("primary")  // initially primary
                .linkedId(null)
                .createdAt(LocalDateTime.of(2023, 4, 21, 5, 30))
                .updatedAt(LocalDateTime.of(2023, 4, 21, 5, 30))
                .build();

        // Step 1: Mock initial fetch by email or phone number returns both contacts
        when(contactRepository.findByEmailOrPhoneNumber("george@hillvalley.edu", "717171"))
                .thenReturn(List.of(c1, c2));

        // Step 2: Mock fetch of linked/transitive contacts returns both (before update)
        when(contactRepository.findAllByLinkedIdInOrIdIn(anySet(), anySet()))
                .thenReturn(List.of(c1, c2));

        // Step 3: Mock saving the update - c2 becomes secondary linked to c1
        Contact updatedC2 = Contact.builder()
                .id(27L)
                .email("biffsucks@hillvalley.edu")
                .phoneNumber("717171")
                .linkPrecedence("secondary")
                .linkedId(11L)
                .createdAt(c2.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();

        when(contactRepository.save(any(Contact.class))).thenReturn(updatedC2);

        // Step 4: Mock final fetch of all linked contacts after update
        when(contactRepository.findAllById(anySet()))
                .thenReturn(List.of(c1, updatedC2));

        // Act
        ContactResponse response = contactService.identifyContact("george@hillvalley.edu", "717171");

        // Assert
        assertNotNull(response);
        assertEquals(11L, response.getPrimaryContactId());
        assertEquals(Set.of("george@hillvalley.edu", "biffsucks@hillvalley.edu"), new HashSet<>(response.getEmails()));
        assertEquals(Set.of("919191", "717171"), new HashSet<>(response.getPhoneNumbers()));
        assertTrue(response.getSecondaryContactIds().contains(27L));
    }







}
