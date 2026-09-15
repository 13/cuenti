package com.cuenti.app.service;

import com.cuenti.app.model.Tag;
import com.cuenti.app.model.User;
import com.cuenti.app.repository.TagRepository;
import com.cuenti.app.repository.TransactionRepository;
import com.cuenti.app.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TagService {
    private final TagRepository tagRepository;
    private final TransactionRepository transactionRepository;
    private final UserService userService;
    private final SecurityUtils securityUtils;

    public List<Tag> getAllTags() {
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        User currentUser = userService.findByUsername(username);
        return tagRepository.findByUser(currentUser);
    }

    public List<Tag> searchTags(String searchTerm) {
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        User currentUser = userService.findByUsername(username);
        if (searchTerm == null || searchTerm.isEmpty()) {
            return tagRepository.findByUser(currentUser);
        }
        return tagRepository.findByUserAndNameContainingIgnoreCase(currentUser, searchTerm);
    }

    /**
     * All tag names known for the current user: managed tags plus names only
     * found on transactions. Case-insensitive duplicates collapse to the first
     * spelling seen (managed tags win), sorted case-insensitively.
     */
    public List<String> getAllTagNames() {
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        User currentUser = userService.findByUsername(username);
        Map<String, String> byLower = new LinkedHashMap<>();
        for (Tag tag : tagRepository.findByUser(currentUser)) {
            addTagName(byLower, tag.getName());
        }
        for (String tags : transactionRepository.findDistinctTagStringsByUser(currentUser)) {
            for (String name : tags.split(",")) {
                addTagName(byLower, name);
            }
        }
        List<String> names = new ArrayList<>(byLower.values());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /** Tag names (see {@link #getAllTagNames()}) containing the term, ignoring case. */
    public List<String> searchTagNames(String searchTerm) {
        List<String> names = getAllTagNames();
        if (searchTerm == null || searchTerm.isBlank()) {
            return names;
        }
        String lower = searchTerm.trim().toLowerCase(Locale.ROOT);
        return names.stream()
                .filter(n -> n.toLowerCase(Locale.ROOT).contains(lower))
                .toList();
    }

    private static void addTagName(Map<String, String> byLower, String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        String trimmed = name.trim();
        byLower.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
    }

    public Optional<Tag> findByName(String name) {
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        User currentUser = userService.findByUsername(username);
        return tagRepository.findByUserAndName(currentUser, name);
    }

    @Transactional
    public Tag saveTag(Tag tag) {
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        User currentUser = userService.findByUsername(username);

        // If it's a new tag, set the user
        if (tag.getId() == null) {
            tag.setUser(currentUser);
        } else {
            // If updating, verify the user owns it
            Tag existing = tagRepository.findById(tag.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Tag not found"));
            if (!existing.getUser().getId().equals(currentUser.getId())) {
                throw new SecurityException("Cannot modify tag belonging to another user");
            }
            tag.setUser(currentUser);
        }
        return tagRepository.save(tag);
    }

    @Transactional
    public void deleteTag(Tag tag) {
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        User currentUser = userService.findByUsername(username);
        // Security check: only allow deletion if tag belongs to current user
        if (tag.getUser().getId().equals(currentUser.getId())) {
            tagRepository.delete(tag);
        } else {
            throw new SecurityException("Cannot delete tag belonging to another user");
        }
    }
}
