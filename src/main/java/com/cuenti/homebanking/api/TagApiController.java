package com.cuenti.homebanking.api;

import com.cuenti.homebanking.api.dto.DtoMapper;
import com.cuenti.homebanking.api.dto.TagDTO;
import com.cuenti.homebanking.model.Tag;
import com.cuenti.homebanking.service.SecurityUtil;
import com.cuenti.homebanking.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagApiController {

    private final TagService tagService;

    @GetMapping
    public ResponseEntity<List<TagDTO>> getTags(@RequestParam(required = false) String search) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        List<Tag> tags = search != null
                ? tagService.searchTags(search)
                : tagService.getAllTags();

        return ResponseEntity.ok(tags.stream()
                .map(DtoMapper::toTagDTO)
                .collect(Collectors.toList()));
    }

    @PostMapping
    public ResponseEntity<TagDTO> createTag(@RequestBody TagDTO dto) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        Tag tag = new Tag();
        tag.setName(dto.getName());
        Tag saved = tagService.saveTag(tag);
        return ResponseEntity.ok(DtoMapper.toTagDTO(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TagDTO> updateTag(@PathVariable Long id, @RequestBody TagDTO dto) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        Tag tag = tagService.getAllTags().stream()
                .filter(t -> t.getId().equals(id))
                .findFirst()
                .orElse(null);
        if (tag == null) return ResponseEntity.notFound().build();

        tag.setName(dto.getName());
        Tag saved = tagService.saveTag(tag);
        return ResponseEntity.ok(DtoMapper.toTagDTO(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTag(@PathVariable Long id) {
        String username = SecurityUtil.getAuthenticatedUsername().orElse(null);
        if (username == null) return ResponseEntity.status(401).build();

        Tag tag = tagService.getAllTags().stream()
                .filter(t -> t.getId().equals(id))
                .findFirst()
                .orElse(null);
        if (tag == null) return ResponseEntity.notFound().build();

        tagService.deleteTag(tag);
        return ResponseEntity.ok().build();
    }
}
