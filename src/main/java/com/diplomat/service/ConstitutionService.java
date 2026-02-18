package com.diplomat.service;

import com.diplomat.model.Constitution;
import com.diplomat.repository.ConstitutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConstitutionService {

    private final ConstitutionRepository constitutionRepository;

    /**
     * Load the default constitution template from classpath.
     */
    public String getTemplate() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/constitution-template.txt");
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load constitution template", e);
            return getHardcodedTemplate();
        }
    }

    /**
     * Create a new constitution from the template or custom content.
     */
    @Transactional
    public Constitution create(String title, String content, String createdBy) {
        Constitution constitution = Constitution.builder()
                .title(title)
                .content(content)
                .createdBy(createdBy)
                .finalized(false)
                .createdAt(LocalDateTime.now())
                .build();
        return constitutionRepository.save(constitution);
    }

    /**
     * Create a constitution from the default template.
     */
    @Transactional
    public Constitution createFromTemplate(String createdBy) {
        return create("Our Communication Constitution", getTemplate(), createdBy);
    }

    @Transactional
    public Constitution update(Long id, String content) {
        Constitution constitution = constitutionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Constitution not found: " + id));
        constitution.setContent(content);
        return constitutionRepository.save(constitution);
    }

    @Transactional
    public Constitution finalize(Long id) {
        Constitution constitution = constitutionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Constitution not found: " + id));
        constitution.setFinalized(true);
        return constitutionRepository.save(constitution);
    }

    public Constitution getById(Long id) {
        return constitutionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Constitution not found: " + id));
    }

    public List<Constitution> getAll() {
        return constitutionRepository.findAll();
    }

    private String getHardcodedTemplate() {
        return """
                # Our Communication Constitution
                
                ## Core Principles
                1. We assume good intent in each other's words.
                2. We speak for ourselves using "I feel..." statements.
                3. We listen to understand, not to respond.
                
                ## Ground Rules
                - No name-calling or personal attacks
                - No bringing up past resolved issues
                - Either person can call a timeout at any time
                - We address one topic at a time
                
                ## When Things Escalate
                - Take a 5-minute break if either person feels overwhelmed
                - Return to the conversation after the break
                - Start the return with something you appreciate about the other person
                """;
    }
}
