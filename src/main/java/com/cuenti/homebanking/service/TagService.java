package com.cuenti.homebanking.service;

import com.cuenti.homebanking.model.Tag;
import com.cuenti.homebanking.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TagService {
    private final TagRepository tagRepository;

    public List<Tag> getAllTags() {
        return tagRepository.findAll();
    }

    public List<Tag> searchTags(String searchTerm) {
        if (searchTerm == null || searchTerm.isEmpty()) {
            return getAllTags();
        }
        return tagRepository.findByNameContainingIgnoreCase(searchTerm);
    }

    @Transactional
    public Tag saveTag(Tag tag) {
        return tagRepository.save(tag);
    }

    @Transactional
    public void deleteTag(Tag tag) {
        tagRepository.delete(tag);
    }
}
