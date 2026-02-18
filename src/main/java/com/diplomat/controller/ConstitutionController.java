package com.diplomat.controller;

import com.diplomat.dto.ConstitutionDto;
import com.diplomat.model.Constitution;
import com.diplomat.service.ConstitutionService;
import com.diplomat.service.DiplomatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/constitution")
@RequiredArgsConstructor
public class ConstitutionController {

    private final ConstitutionService constitutionService;
    private final DiplomatService diplomatService;

    /**
     * Get the default template.
     */
    @GetMapping("/template")
    public ResponseEntity<Map<String, String>> getTemplate() {
        return ResponseEntity.ok(Map.of("template", constitutionService.getTemplate()));
    }

    /**
     * Create a new constitution from template.
     */
    @PostMapping("/from-template")
    public ResponseEntity<Constitution> createFromTemplate(@RequestBody Map<String, String> body) {
        String createdBy = body.getOrDefault("createdBy", "TEMPLATE");
        return ResponseEntity.ok(constitutionService.createFromTemplate(createdBy));
    }

    /**
     * Create a custom constitution.
     */
    @PostMapping
    public ResponseEntity<Constitution> create(@RequestBody ConstitutionDto dto) {
        return ResponseEntity.ok(constitutionService.create(dto.getTitle(), dto.getContent(), "CUSTOM"));
    }

    /**
     * Update a constitution.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Constitution> update(@PathVariable Long id, @RequestBody ConstitutionDto dto) {
        return ResponseEntity.ok(constitutionService.update(id, dto.getContent()));
    }

    /**
     * Finalize (both parties agree).
     */
    @PostMapping("/{id}/finalize")
    public ResponseEntity<Constitution> finalizeConstitution(@PathVariable Long id) {
        return ResponseEntity.ok(constitutionService.finalize(id));
    }

    /**
     * Get AI suggestions to improve the constitution.
     */
    @PostMapping("/{id}/suggest")
    public ResponseEntity<Map<String, String>> suggest(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Constitution current = constitutionService.getById(id);
        String request = body.get("request");
        String suggestion = diplomatService.suggestConstitutionImprovement(current.getContent(), request);
        return ResponseEntity.ok(Map.of("suggestion", suggestion));
    }

    /**
     * Get all constitutions.
     */
    @GetMapping
    public ResponseEntity<List<Constitution>> getAll() {
        return ResponseEntity.ok(constitutionService.getAll());
    }

    /**
     * Get a constitution by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Constitution> getById(@PathVariable Long id) {
        return ResponseEntity.ok(constitutionService.getById(id));
    }
}
