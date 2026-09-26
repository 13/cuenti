package com.cuenti.app.views.components;

import com.cuenti.app.service.TagService;
import com.cuenti.app.util.TagNames;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.renderer.ComponentRenderer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One field for a transaction's tags: pick existing tags from a coloured list, or type
 * a new name and press Enter to create it (existing names are reused ignoring case,
 * so no duplicates). Below the field, tags previously used with the current payee are
 * offered as one-click suggestions. The value is the stored comma-separated string.
 */
public class TagField extends CustomField<String> {

    private static final int SUGGESTION_LIMIT = 5;

    private final TagService tagService;
    private final MultiSelectComboBox<String> combo = new MultiSelectComboBox<>();
    private final Div suggestions = new Div();
    private final com.vaadin.flow.dom.Element chipStyles = new com.vaadin.flow.dom.Element("style");
    private final List<String> items = new ArrayList<>();
    private List<String> suggested = List.of();

    public TagField(TagService tagService, String label) {
        this.tagService = tagService;
        setLabel(label);
        addClassName("tag-field");

        items.addAll(tagService.getAllTagNames());
        combo.setItems(items);
        combo.setWidthFull();
        combo.setAllowCustomValue(true);
        combo.setSelectedItemsOnTop(true);
        combo.setAutoExpand(MultiSelectComboBox.AutoExpandMode.VERTICAL);
        combo.setPlaceholder(getTranslation("tags.field_placeholder"));
        combo.setRenderer(new ComponentRenderer<>(TagColorUtil::createTagBadge));
        // Chips take their tag colour through a class; the matching rules live in chipStyles.
        combo.setClassNameGenerator(TagField::chipClassFor);
        combo.setAriaLabel(label);
        combo.addCustomValueSetListener(e -> addTags(List.of(e.getDetail())));
        combo.addValueChangeListener(e -> {
            renderSuggestions();
            updateValue();
        });

        suggestions.addClassName("tag-field__suggestions");
        suggestions.setVisible(false);

        setHelperText(getTranslation("tags.field_helper"));

        Div wrapper = new Div(combo, suggestions);
        wrapper.getElement().appendChild(chipStyles);
        refreshChipStyles();
        wrapper.setWidthFull();
        wrapper.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "var(--vaadin-gap-xs)");
        add(wrapper);
    }

    /** Adds tags to the selection, creating unknown ones; already selected names are ignored. */
    public void addTags(Collection<String> names) {
        Set<String> selection = new LinkedHashSet<>(combo.getValue());
        for (String name : TagNames.parse(String.join(",", names))) {
            String item = items.stream().filter(i -> TagNames.same(i, name)).findFirst()
                    .orElseGet(() -> {
                        String created = tagService.findOrCreate(name).getName();
                        items.add(created);
                        items.sort(String.CASE_INSENSITIVE_ORDER);
                        combo.getListDataView().refreshAll();
                        refreshChipStyles();
                        return created;
                    });
            selection.add(item);
        }
        combo.setValue(selection);
    }

    /** Offers the tags most often used with this payee as clickable chips. */
    public void setSuggestionsFor(String payee) {
        suggested = tagService.suggestTagNames(payee, SUGGESTION_LIMIT);
        renderSuggestions();
    }

    /** The inner combo box, for tests. */
    public MultiSelectComboBox<String> getComboBox() {
        return combo;
    }

    @Override
    protected String generateModelValue() {
        return TagNames.join(combo.getValue());
    }

    @Override
    protected void setPresentationValue(String value) {
        Set<String> selection = new LinkedHashSet<>();
        for (String name : TagNames.parse(value)) {
            // Names only found on transactions (e.g. after an import) stay selectable as they are.
            String item = items.stream().filter(i -> TagNames.same(i, name)).findFirst().orElseGet(() -> {
                items.add(name);
                combo.getListDataView().refreshAll();
                refreshChipStyles();
                return name;
            });
            selection.add(item);
        }
        combo.setValue(selection);
    }

    @Override
    public void setReadOnly(boolean readOnly) {
        super.setReadOnly(readOnly);
        combo.setReadOnly(readOnly);
        renderSuggestions();
    }

    /** CSS class carrying a tag's colours (hex values, so equal colours share a class). */
    public static String chipClassFor(String tag) {
        String[] c = TagColorUtil.colors(tag);
        return "tagc-" + c[0].substring(1) + ("#FFFFFF".equals(c[1]) ? "-l" : "-d");
    }

    /** One rule per distinct tag colour; chips live in light DOM, so document CSS reaches them. */
    private void refreshChipStyles() {
        StringBuilder css = new StringBuilder();
        items.stream().map(TagField::chipClassFor).distinct().forEach(cls -> {
            String hex = cls.substring(5, 11);
            String fg = cls.endsWith("-l") ? "#FFFFFF" : "#111111";
            css.append("vaadin-multi-select-combo-box-chip.").append(cls)
                    .append("{background:#").append(hex).append(";color:").append(fg)
                    .append(";--vaadin-multi-select-combo-box-chip-remove-color:").append(fg).append(";}");
        });
        chipStyles.setText(css.toString());
    }

    private void renderSuggestions() {
        suggestions.removeAll();
        if (isReadOnly()) {
            suggestions.setVisible(false);
            return;
        }
        Set<String> selected = combo.getValue();
        List<String> open = suggested.stream()
                .filter(s -> selected.stream().noneMatch(sel -> TagNames.same(sel, s)))
                .toList();
        if (open.isEmpty()) {
            suggestions.setVisible(false);
            return;
        }
        Span hint = new Span(getTranslation("tags.suggestions"));
        hint.addClassName("tag-field__hint");
        suggestions.add(hint);
        for (String name : open) {
            Span chip = TagColorUtil.createTagBadge(name); // colour follows the tag name
            chip.setText("+ " + name);
            chip.addClassName("tag-field__suggestion");
            chip.getElement().setAttribute("role", "button");
            chip.getElement().setAttribute("tabindex", "0");
            chip.getElement().setAttribute("title", getTranslation("tags.suggestion_add", name));
            chip.getElement().addEventListener("click", e -> addTags(List.of(name)));
            chip.getElement().addEventListener("keydown", e -> addTags(List.of(name)))
                    .setFilter("event.key === 'Enter' || event.key === ' '");
            suggestions.add(chip);
        }
        suggestions.setVisible(true);
    }
}
