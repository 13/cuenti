package com.cuenti.app.service;

import com.cuenti.app.model.SavedView;
import com.cuenti.app.model.User;
import com.cuenti.app.repository.SavedViewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SavedViewService {

    private final SavedViewRepository repository;

    @Transactional(readOnly = true)
    public List<SavedView> getViews(User user) {
        return repository.findByUserOrderByNameAsc(user);
    }

    /** Creates a view, or overwrites the params of an existing view with the same name. */
    @Transactional
    public SavedView save(User user, String name, String params) {
        SavedView view = repository.findByUserAndName(user, name)
                .orElseGet(() -> SavedView.builder().user(user).name(name).build());
        view.setParams(params);
        return repository.save(view);
    }

    @Transactional
    public void delete(User user, SavedView view) {
        if (!view.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Cannot delete another user's saved view");
        }
        repository.delete(view);
    }
}
