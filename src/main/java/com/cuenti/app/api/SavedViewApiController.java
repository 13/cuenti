package com.cuenti.app.api;

import com.cuenti.app.api.dto.DtoMapper;
import com.cuenti.app.api.dto.SavedViewDTO;
import com.cuenti.app.model.SavedView;
import com.cuenti.app.model.User;
import com.cuenti.app.service.SavedViewService;
import com.cuenti.app.service.SecurityUtil;
import com.cuenti.app.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/saved-views")
@RequiredArgsConstructor
public class SavedViewApiController {

    private final SavedViewService savedViewService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<SavedViewDTO>> getViews() {
        User user = currentUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(savedViewService.getViews(user).stream()
                .map(DtoMapper::toSavedViewDTO)
                .collect(Collectors.toList()));
    }

    @PostMapping
    public ResponseEntity<SavedViewDTO> createView(@RequestBody SavedViewDTO dto) {
        User user = currentUser();
        if (user == null) return ResponseEntity.status(401).build();
        if (dto.getName() == null || dto.getName().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        SavedView saved = savedViewService.save(user, dto.getName(), dto.getParams());
        return ResponseEntity.ok(DtoMapper.toSavedViewDTO(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SavedViewDTO> updateView(@PathVariable Long id, @RequestBody SavedViewDTO dto) {
        User user = currentUser();
        if (user == null) return ResponseEntity.status(401).build();
        try {
            SavedView updated = savedViewService.update(user, id, dto.getName(), dto.getParams());
            return ResponseEntity.ok(DtoMapper.toSavedViewDTO(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteView(@PathVariable Long id) {
        User user = currentUser();
        if (user == null) return ResponseEntity.status(401).build();
        SavedView view = savedViewService.getViews(user).stream()
                .filter(v -> v.getId().equals(id))
                .findFirst()
                .orElse(null);
        if (view == null) return ResponseEntity.notFound().build();
        savedViewService.delete(user, view);
        return ResponseEntity.ok().build();
    }

    private User currentUser() {
        return SecurityUtil.getAuthenticatedUsername()
                .map(userService::findByUsername)
                .orElse(null);
    }
}
