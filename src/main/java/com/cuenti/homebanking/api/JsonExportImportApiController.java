package com.cuenti.homebanking.api;

import com.cuenti.homebanking.model.User;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.service.JsonExportImportService;
import com.cuenti.homebanking.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/json-export-import")
@RequiredArgsConstructor
public class JsonExportImportApiController {

    private final JsonExportImportService jsonExportImportService;
    private final UserService userService;
    private final SecurityUtils securityUtils;

    @GetMapping("/export")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> exportUserData() {
        try {
            String username = securityUtils.getAuthenticatedUsername().orElseThrow();
            User user = userService.findByUsername(username);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            jsonExportImportService.exportUserData(user, outputStream);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = String.format("cuenti_export_%s_%s.json", username, timestamp);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setContentDispositionFormData("attachment", filename);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(outputStream.toByteArray());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/import")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> importUserData(@RequestParam("file") MultipartFile file) {
        try {
            String username = securityUtils.getAuthenticatedUsername().orElseThrow();
            User user = userService.findByUsername(username);

            jsonExportImportService.importUserData(user, file.getInputStream());

            return ResponseEntity.ok("Data imported successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Import failed: " + e.getMessage());
        }
    }
}
