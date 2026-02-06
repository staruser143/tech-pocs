package com.example.demo.docgen.controller;

import com.example.demo.docgen.model.DocumentGenerationRequest;
import com.example.demo.docgen.service.DocumentComposer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for document generation
 */
@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {
    private final DocumentComposer documentComposer;
    
    /**
     * Generate a PDF document from a template and data
     *
     * POST /api/documents/generate
     * {
     *   "templateId": "templates/enrollment-form.yaml",
     *   "data": {
     *     "applicant": { "firstName": "John", "lastName": "Doe" },
     *     ...
     *   }
     * }
     *
     * @param request Generation request
     * @return PDF document as byte array
     */
    @PostMapping("/generate")
    public ResponseEntity<byte[]> generateDocument(@RequestBody DocumentGenerationRequest request) {
        log.info("Received document generation request for template: {}", request.getTemplateId());
        
        try {
            byte[] pdf = documentComposer.generateDocument(request);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "document.pdf");
            headers.setContentLength(pdf.length);
            
            return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
            
        } catch (Exception e) {
            log.error("Document generation failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Document generation service is running");
    }
}
