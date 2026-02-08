package com.cuenti.homebanking.views.scheduled;

import com.cuenti.homebanking.data.*;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.services.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.vaadin.lineawesome.LineAwesomeIconUrl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@PageTitle("Scheduled Transactions")
@Route("scheduled")
@Menu(order = 2, icon = LineAwesomeIconUrl.CALENDAR_ALT)
@PermitAll
public class ScheduledTransactionsView extends VerticalLayout {

    private final ScheduledTransactionService scheduledTransactionService;
    private final UserService userService;
    private final SecurityUtils securityUtils;
    private final User currentUser;

    private final Grid<ScheduledTransaction> scheduleGrid = new Grid<>(ScheduledTransaction.class, false);
    private final Grid<ScheduledTransaction> pendingGrid = new Grid<>(ScheduledTransaction.class, false);

    public ScheduledTransactionsView(ScheduledTransactionService scheduledTransactionService,
                                      UserService userService, SecurityUtils securityUtils) {
        this.scheduledTransactionService = scheduledTransactionService;
        this.userService = userService;
        this.securityUtils = securityUtils;

        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new IllegalStateException("User not authenticated"));
        this.currentUser = userService.findByUsername(username);

        setSpacing(true);
        setPadding(true);
        setMaxWidth("1200px");
        getStyle().set("margin", "0 auto");

        setupUI();
        refreshGrids();
    }

    private void setupUI() {
        H2 title = new H2(getTranslation("scheduled.title"));
        title.getStyle().set("margin-top", "0").set("color", "var(--lumo-primary-text-color)");

        Button addButton = new Button(getTranslation("scheduled.new"), VaadinIcon.PLUS.create());
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addButton.addClickListener(e -> {
            Notification.show("Schedule creation not yet implemented", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_PRIMARY);
        });

        HorizontalLayout header = new HorizontalLayout(title, addButton);
        header.setWidthFull();
        header.setAlignItems(Alignment.CENTER);
        header.expand(title);

        // Pending occurrences card
        Div pendingCard = createCard();
        H3 pendingTitle = new H3(getTranslation("scheduled.pending_title"));
        pendingTitle.getStyle().set("margin", "0");
        setupPendingGrid();
        pendingCard.add(pendingTitle, pendingGrid);

        // All schedules card
        Div schedulesCard = createCard();
        H3 schedulesTitle = new H3(getTranslation("scheduled.list_title"));
        schedulesTitle.getStyle().set("margin", "0");
        setupScheduleGrid();
        schedulesCard.add(schedulesTitle, scheduleGrid);

        add(header, pendingCard, schedulesCard);
    }

    private Div createCard() {
        Div card = new Div();
        card.setWidthFull();
        card.getStyle()
                .set("background-color", "var(--lumo-base-color)")
                .set("border-radius", "16px")
                .set("padding", "var(--lumo-space-l)")
                .set("box-shadow", "var(--lumo-box-shadow-m)")
                .set("margin-bottom", "var(--lumo-space-m)");
        return card;
    }

    private void setupPendingGrid() {
        pendingGrid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        pendingGrid.addComponentColumn(st -> {
            LocalDateTime next = st.getNextOccurrence();
            boolean isLate = next != null && next.isBefore(LocalDateTime.now());
            Span span = new Span(next != null ? next.format(formatter) : "-");
            if (isLate) {
                span.getStyle().set("color", "var(--lumo-error-color)");
                span.setText(span.getText() + " ⚠");
            }
            return span;
        }).setHeader(getTranslation("scheduled.due_date")).setAutoWidth(true);

        pendingGrid.addColumn(ScheduledTransaction::getPayee).setHeader("Payee").setAutoWidth(true);
        pendingGrid.addColumn(ScheduledTransaction::getAmount).setHeader("Amount").setAutoWidth(true);
        pendingGrid.addColumn(st -> st.getRecurrencePattern().name()).setHeader(getTranslation("scheduled.recurrence")).setAutoWidth(true);

        pendingGrid.addComponentColumn(st -> {
            Button postBtn = new Button(getTranslation("scheduled.post"), e -> {
                scheduledTransactionService.post(st.getId());
                refreshGrids();
                Notification.show(getTranslation("scheduled.posted"));
            });
            postBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

            Button skipBtn = new Button(getTranslation("scheduled.skip"), e -> {
                scheduledTransactionService.skip(st.getId());
                refreshGrids();
                Notification.show(getTranslation("scheduled.skipped"));
            });
            skipBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

            return new HorizontalLayout(postBtn, skipBtn);
        }).setHeader("Actions").setAutoWidth(true);
    }

    private void setupScheduleGrid() {
        scheduleGrid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        scheduleGrid.addColumn(st -> st.getNextOccurrence() != null ? st.getNextOccurrence().format(formatter) : "-")
                .setHeader(getTranslation("scheduled.next_date")).setAutoWidth(true).setSortable(true);
        scheduleGrid.addColumn(ScheduledTransaction::getPayee).setHeader("Payee").setAutoWidth(true).setSortable(true);
        scheduleGrid.addColumn(ScheduledTransaction::getAmount).setHeader("Amount").setAutoWidth(true);
        scheduleGrid.addColumn(st -> st.getType().name()).setHeader("Type").setAutoWidth(true);
        scheduleGrid.addColumn(st -> st.getRecurrencePattern().name()).setHeader(getTranslation("scheduled.recurrence")).setAutoWidth(true);

        scheduleGrid.addComponentColumn(st -> {
            Span span = new Span(st.isEnabled() ? "✓" : "✗");
            span.getStyle().set("color", st.isEnabled() ? "var(--lumo-success-color)" : "var(--lumo-error-color)");
            return span;
        }).setHeader(getTranslation("scheduled.enabled")).setAutoWidth(true);

        scheduleGrid.addComponentColumn(st -> {
            Button editBtn = new Button(VaadinIcon.EDIT.create());
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            editBtn.addClickListener(e -> {
                Notification.show("Edit not yet implemented", 3000, Notification.Position.MIDDLE);
            });

            Button deleteBtn = new Button(VaadinIcon.TRASH.create(), e -> {
                scheduledTransactionService.delete(st);
                refreshGrids();
                Notification.show("Deleted");
            });
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);

            return new HorizontalLayout(editBtn, deleteBtn);
        }).setHeader("Actions").setAutoWidth(true);
    }

    private void refreshGrids() {
        List<ScheduledTransaction> all = scheduledTransactionService.getByUser(currentUser);
        scheduleGrid.setItems(all);

        // Pending = enabled and next occurrence <= now
        List<ScheduledTransaction> pending = all.stream()
                .filter(st -> st.isEnabled() && st.getNextOccurrence() != null &&
                        st.getNextOccurrence().isBefore(LocalDateTime.now().plusDays(7)))
                .collect(Collectors.toList());
        pendingGrid.setItems(pending);
    }
}
