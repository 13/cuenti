package com.cuenti.app.views;

import com.cuenti.app.model.AuditLog;
import com.cuenti.app.service.AuditService;
import com.cuenti.app.views.components.EmptyStateNotice;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.time.format.DateTimeFormatter;

/**
 * Admin-only, read-only view of the audit log: who changed what, when.
 */
@Route(value = "settings/audit", layout = MainLayout.class)
@RolesAllowed("ROLE_ADMIN")
public class AuditLogView extends VerticalLayout implements HasDynamicTitle {

    private static final int PAGE_SIZE = 50;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    @Override
    public String getPageTitle() {
        return getTranslation("audit.title") + " | " + getTranslation("app.name");
    }

    private final AuditService auditService;
    private final Grid<AuditLog> grid = new Grid<>(AuditLog.class, false);
    final TextField filterField = new TextField(); // package-visible for tests
    private final Span pageInfo = new Span();
    private final Button prevBtn = new Button(VaadinIcon.ANGLE_LEFT.create());
    private final Button nextBtn = new Button(VaadinIcon.ANGLE_RIGHT.create());
    private int page = 0;

    public AuditLogView(AuditService auditService) {
        this.auditService = auditService;

        addClassNames("page-scroll", "page-shell");
        setSizeFull();
        setPadding(false);
        setSpacing(false);

        Span title = new Span(getTranslation("audit.title"));
        title.addComponentAsFirst(VaadinIcon.CLIPBOARD_TEXT.create());
        title.addClassName("page-title");

        filterField.setPlaceholder(getTranslation("audit.filter"));
        filterField.setPrefixComponent(VaadinIcon.SEARCH.create());
        filterField.setClearButtonVisible(true);
        filterField.setValueChangeMode(ValueChangeMode.LAZY);
        filterField.setWidth("260px");
        filterField.addValueChangeListener(e -> {
            page = 0;
            refresh();
        });

        prevBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        prevBtn.getElement().setAttribute("aria-label", getTranslation("audit.prev_page"));
        prevBtn.addClickListener(e -> {
            if (page > 0) {
                page--;
                refresh();
            }
        });
        nextBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        nextBtn.getElement().setAttribute("aria-label", getTranslation("audit.next_page"));
        nextBtn.addClickListener(e -> {
            page++;
            refresh();
        });
        pageInfo.getStyle().set("font-size", "var(--aura-font-size-s)")
                .set("color", "var(--vaadin-text-color-secondary)");

        HorizontalLayout pager = new HorizontalLayout(pageInfo, prevBtn, nextBtn);
        pager.setAlignItems(Alignment.CENTER);
        pager.setSpacing(false);
        pager.getStyle().set("gap", "var(--vaadin-gap-xs)");

        HorizontalLayout toolbar = new HorizontalLayout(filterField, pager);
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.setJustifyContentMode(JustifyContentMode.BETWEEN);
        toolbar.addClassName("card-toolbar");
        toolbar.getStyle().set("flex-wrap", "wrap");

        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_COMPACT);
        grid.setSizeFull();
        grid.setEmptyStateComponent(new EmptyStateNotice(
                VaadinIcon.CLIPBOARD_TEXT, getTranslation("audit.empty"), null));

        grid.addColumn(l -> TS.format(l.getTimestamp()))
                .setHeader(getTranslation("audit.time")).setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(AuditLog::getUsername)
                .setHeader(getTranslation("audit.user")).setAutoWidth(true);
        grid.addComponentColumn(this::actionPill)
                .setHeader(getTranslation("audit.action")).setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(AuditLog::getEntityType)
                .setHeader(getTranslation("audit.entity")).setAutoWidth(true);
        grid.addColumn(AuditLog::getDetails)
                .setHeader(getTranslation("audit.details")).setFlexGrow(1);

        Div card = new Div(toolbar, grid);
        card.setSizeFull();
        card.addClassNames("card", "card--flex");

        add(title, card);
        expand(card);
        refresh();
    }

    private Span actionPill(AuditLog log) {
        Span pill = new Span(log.getAction());
        pill.addClassName("pill-tint");
        String color = switch (log.getAction()) {
            case "CREATE" -> "var(--aura-green)";
            case "DELETE" -> "var(--aura-red)";
            default -> "var(--aura-accent-color)";
        };
        pill.getStyle().set("--pill-color", color);
        return pill;
    }

    private void refresh() {
        var result = auditService.latest(filterField.getValue(), page, PAGE_SIZE);
        grid.setItems(result.getContent());
        pageInfo.setText((page + 1) + " / " + Math.max(1, result.getTotalPages()));
        prevBtn.setEnabled(page > 0);
        nextBtn.setEnabled(page + 1 < result.getTotalPages());
    }
}
