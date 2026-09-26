package com.cuenti.app.service;

import com.cuenti.app.model.Tag;
import com.cuenti.app.model.User;
import com.cuenti.app.repository.TagRepository;
import com.cuenti.app.repository.PayeeRepository;
import com.cuenti.app.repository.ScheduledTransactionRepository;
import com.cuenti.app.repository.TransactionRepository;
import com.cuenti.app.model.Payee;
import com.cuenti.app.model.ScheduledTransaction;
import com.cuenti.app.model.Transaction;
import com.cuenti.app.util.TagNames;
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
    private final ScheduledTransactionRepository scheduledTransactionRepository;
    private final PayeeRepository payeeRepository;
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
        User currentUser = currentUser();
        // Every dialog with a tag field asks for this; scanning all transactions each time is wasteful.
        CachedNames cached = nameCache.get(currentUser.getId());
        if (cached != null && cached.loadedAt() > System.currentTimeMillis() - NAME_CACHE_MILLIS) {
            return cached.names();
        }
        List<String> names = loadTagNames(currentUser);
        nameCache.put(currentUser.getId(), new CachedNames(names, System.currentTimeMillis()));
        return names;
    }

    /** Forget the cached names so the next tag field sees tag changes at once. */
    public void invalidateNames() {
        securityUtils.getAuthenticatedUsername()
                .map(userService::findByUsername)
                .ifPresent(u -> nameCache.remove(u.getId()));
    }

    /** Forget one user's cached names (a transaction with tags was saved). */
    public void invalidateNames(User user) {
        if (user != null && user.getId() != null) {
            nameCache.remove(user.getId());
        }
    }

    private static final long NAME_CACHE_MILLIS = 60_000;

    private record CachedNames(List<String> names, long loadedAt) {
    }

    private final Map<Long, CachedNames> nameCache = new java.util.concurrent.ConcurrentHashMap<>();

    private List<String> loadTagNames(User currentUser) {
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
        return List.copyOf(names);
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

    /**
     * The user's tag with this name ignoring case, created when missing. Every UI
     * path that turns typed text into a tag goes through here so no duplicate
     * tag rows appear.
     */
    @Transactional
    public Tag findOrCreate(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Tag name must not be blank");
        }
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        User currentUser = userService.findByUsername(username);
        return tagRepository.findByUserAndNameIgnoreCase(currentUser, trimmed).stream()
                .findFirst()
                .orElseGet(() -> {
                    nameCache.remove(currentUser.getId());
                    return tagRepository.save(Tag.builder().name(trimmed).user(currentUser).build());
                });
    }

    /**
     * Tag names the current user attached to earlier transactions with this payee,
     * most used first. Feeds the quick suggestions in the tag field.
     */
    public List<String> suggestTagNames(String payee, int limit) {
        if (payee == null || payee.isBlank()) {
            return List.of();
        }
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        User currentUser = userService.findByUsername(username);
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> spelling = new LinkedHashMap<>();
        for (String tags : transactionRepository.findTagStringsByUserAndPayee(currentUser, payee.trim())) {
            for (String name : com.cuenti.app.util.TagNames.parse(tags)) {
                String key = name.toLowerCase(Locale.ROOT);
                spelling.putIfAbsent(key, name);
                counts.merge(key, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(e -> spelling.get(e.getKey()))
                .toList();
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
        nameCache.remove(currentUser.getId());
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
            nameCache.remove(currentUser.getId());
        } else {
            throw new SecurityException("Cannot delete tag belonging to another user");
        }
    }

    // ── Tags are stored by name on transactions, schedules and payee defaults. ──
    // Renaming, merging or deleting a tag has to rewrite those strings too, or the
    // old name lingers on bookings and reappears in the tag list.

    /** How many transactions, schedules and payee defaults carry this tag name. */
    public record TagUsage(int transactions, int schedules, int payees) {
        public int total() {
            return transactions + schedules + payees;
        }
    }

    /** Stored tag strings before a delete, so an undo can put them back. */
    public record TagRemoval(Tag tag, Map<Long, String> transactions,
                             Map<Long, String> schedules, Map<Long, String> payees) {
    }

    @Transactional(readOnly = true)
    public TagUsage usage(String name) {
        User currentUser = currentUser();
        return new TagUsage(
                taggedTransactions(currentUser, name).size(),
                taggedSchedules(currentUser, name).size(),
                taggedPayees(currentUser, name).size());
    }

    /**
     * Renames the tag everywhere it is used. When another tag already has the new
     * name (ignoring case) the two are merged: this tag row goes away and its uses
     * take the other tag's spelling.
     *
     * @return the tag that carries the name afterwards
     */
    @Transactional
    public Tag rename(Tag tag, String newName) {
        User currentUser = currentUser();
        Tag existing = ownedTag(tag, currentUser);
        String target = newName == null ? "" : newName.trim();
        if (target.isEmpty()) {
            throw new IllegalArgumentException("Tag name must not be blank");
        }
        String oldName = existing.getName();
        Tag other = tagRepository.findByUserAndNameIgnoreCase(currentUser, target).stream()
                .filter(t -> !t.getId().equals(existing.getId()))
                .findFirst().orElse(null);
        Tag result;
        if (other != null) {
            target = other.getName();
            tagRepository.delete(existing);
            result = other;
        } else {
            existing.setName(target);
            result = tagRepository.save(existing);
        }
        rewrite(currentUser, oldName, target);
        nameCache.remove(currentUser.getId());
        return result;
    }

    /** Deletes the tag and removes it from every transaction, schedule and payee default. */
    @Transactional
    public TagRemoval deleteEverywhere(Tag tag) {
        User currentUser = currentUser();
        Tag existing = ownedTag(tag, currentUser);
        String name = existing.getName();
        Map<Long, String> tx = new LinkedHashMap<>();
        Map<Long, String> sched = new LinkedHashMap<>();
        Map<Long, String> payees = new LinkedHashMap<>();
        taggedTransactions(currentUser, name).forEach(t -> tx.put(t.getId(), t.getTags()));
        taggedSchedules(currentUser, name).forEach(s -> sched.put(s.getId(), s.getTags()));
        taggedPayees(currentUser, name).forEach(p -> payees.put(p.getId(), p.getDefaultTags()));
        rewrite(currentUser, name, null);
        tagRepository.delete(existing);
        nameCache.remove(currentUser.getId());
        Tag snapshot = Tag.builder().name(name).build();
        return new TagRemoval(snapshot, tx, sched, payees);
    }

    /** Undo for {@link #deleteEverywhere}: recreates the tag and restores the stored strings. */
    @Transactional
    public Tag restore(TagRemoval removal) {
        User currentUser = currentUser();
        nameCache.remove(currentUser.getId());
        Tag tag = findOrCreate(removal.tag().getName());
        removal.transactions().forEach((id, tags) -> transactionRepository.findById(id)
                .filter(t -> ownsTransaction(t, currentUser))
                .ifPresent(t -> { t.setTags(tags); t.touch(); transactionRepository.save(t); }));
        removal.schedules().forEach((id, tags) -> scheduledTransactionRepository.findById(id)
                .filter(s -> s.getUser().getId().equals(currentUser.getId()))
                .ifPresent(s -> { s.setTags(tags); scheduledTransactionRepository.save(s); }));
        removal.payees().forEach((id, tags) -> payeeRepository.findById(id)
                .filter(p -> p.getUser() != null && p.getUser().getId().equals(currentUser.getId()))
                .ifPresent(p -> { p.setDefaultTags(tags); payeeRepository.save(p); }));
        return tag;
    }

    private void rewrite(User user, String oldName, String newName) {
        for (Transaction t : taggedTransactions(user, oldName)) {
            t.setTags(TagNames.replace(t.getTags(), oldName, newName));
            t.touch();
            transactionRepository.save(t);
        }
        for (ScheduledTransaction s : taggedSchedules(user, oldName)) {
            s.setTags(TagNames.replace(s.getTags(), oldName, newName));
            scheduledTransactionRepository.save(s);
        }
        for (Payee p : taggedPayees(user, oldName)) {
            p.setDefaultTags(TagNames.replace(p.getDefaultTags(), oldName, newName));
            payeeRepository.save(p);
        }
    }

    private List<Transaction> taggedTransactions(User user, String name) {
        return transactionRepository.findByUserAndTagsContaining(user, name.trim()).stream()
                .filter(t -> TagNames.contains(t.getTags(), name))
                .toList();
    }

    private List<ScheduledTransaction> taggedSchedules(User user, String name) {
        return scheduledTransactionRepository.findByUser(user).stream()
                .filter(s -> TagNames.contains(s.getTags(), name))
                .toList();
    }

    private List<Payee> taggedPayees(User user, String name) {
        return payeeRepository.findByUser(user).stream()
                .filter(p -> TagNames.contains(p.getDefaultTags(), name))
                .toList();
    }

    private static boolean ownsTransaction(Transaction t, User user) {
        return (t.getFromAccount() != null && t.getFromAccount().getUser().getId().equals(user.getId()))
                || (t.getToAccount() != null && t.getToAccount().getUser().getId().equals(user.getId()));
    }

    private Tag ownedTag(Tag tag, User user) {
        Tag existing = tagRepository.findById(tag.getId())
                .orElseThrow(() -> new IllegalArgumentException("Tag not found"));
        if (!existing.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Cannot modify tag belonging to another user");
        }
        return existing;
    }

    private User currentUser() {
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        return userService.findByUsername(username);
    }
}
