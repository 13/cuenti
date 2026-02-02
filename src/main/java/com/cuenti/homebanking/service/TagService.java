package com.cuenti.homebanking.service;

import com.cuenti.homebanking.model.Tag;
import com.cuenti.homebanking.model.User;
import com.cuenti.homebanking.repository.TagRepository;
import com.cuenti.homebanking.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TagService {
    private final TagRepository tagRepository;
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
