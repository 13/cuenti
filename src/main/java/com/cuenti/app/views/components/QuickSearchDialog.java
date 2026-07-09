package com.cuenti.app.views.components;

import com.cuenti.app.service.CategoryService;
import com.cuenti.app.service.PayeeService;
import com.cuenti.app.service.TagService;
import com.cuenti.app.views.TransactionHistoryView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.QueryParameters;

import java.util.Locale;

/**
 * Global quick search (Ctrl+K): matches payees, categories and tags and
 * jumps into the transactions view filtered by the chosen term.
 */
public class QuickSearchDialog extends Dialog {

    private static final int MAX_RESULTS = 12;

    private final PayeeService payeeService;
    private final CategoryService categoryService;
    private final TagService tagService;

    private final TextField input = new TextField();
    private final Div results = new Div();

    public QuickSearchDialog(PayeeService payeeService, CategoryService categoryService,
                             TagService tagService) {
        this.payeeService = payeeService;
        this.categoryService = categoryService;
        this.tagService = tagService;

        setHeaderTitle(getTranslation("search.global_title"));
        setWidth("min(520px, 96vw)");
        setCloseOnOutsideClick(true);

        input.setPlaceholder(getTranslation("search.global_hint"));
        input.setPrefixComponent(VaadinIcon.SEARCH.create());
        input.setClearButtonVisible(true);
        input.setWidthFull();
        input.setValueChangeMode(ValueChangeMode.LAZY);
        input.addValueChangeListener(e -> updateResults(e.getValue()));
        // Enter jumps straight to the transactions view with the raw term
        input.addKeyDownListener(com.vaadin.flow.component.Key.ENTER, e -> jump(input.getValue()));

        results.getStyle().set("display", "flex").set("flex-direction", "column")
                .set("gap", "2px").set("margin-top", "var(--vaadin-gap-s)")
                .set("max-height", "50vh").set("overflow-y", "auto");

        Div body = new Div(input, results);
        body.getStyle().set("padding", "var(--vaadin-gap-s) 0").set("min-width", "0");
        add(body);

        addOpenedChangeListener(e -> {
            if (e.isOpened()) {
                input.clear();
                results.removeAll();
                input.focus();
            }
        });
    }

    private void updateResults(String term) {
        results.removeAll();
        if (term == null || term.isBlank()) {
            return;
        }
        String lower = term.toLowerCase(Locale.ROOT);
        int[] budget = {MAX_RESULTS};

        payeeService.searchPayees(term).stream()
                .limit(5)
                .forEach(p -> addResult(VaadinIcon.USERS, p.getName(), budget));

        categoryService.getAllCategories().stream()
                .filter(c -> c.getFullName().toLowerCase(Locale.ROOT).contains(lower))
                .limit(5)
                .forEach(c -> addResult(VaadinIcon.SITEMAP, c.getFullName(), budget));

        tagService.searchTags(term).stream()
                .limit(4)
                .forEach(t -> addResult(VaadinIcon.TAGS, t.getName(), budget));

        Button all = new Button(getTranslation("search.show_all"),
                VaadinIcon.ARROW_RIGHT.create(), e -> jump(term));
        all.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        results.add(all);
    }

    private void addResult(VaadinIcon icon, String label, int[] budget) {
        if (budget[0]-- <= 0) {
            return;
        }
        Button item = new Button(label, icon.create(), e -> jump(label));
        item.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        item.getStyle().set("justify-content", "flex-start");
        item.setWidthFull();
        results.add(item);
    }

    private void jump(String term) {
        if (term == null || term.isBlank()) {
            return;
        }
        close();
        UI.getCurrent().navigate(TransactionHistoryView.class,
                QueryParameters.of("q", term));
    }
}
