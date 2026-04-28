package com.cuenti.homebanking.views;

import com.vaadin.flow.component.accordion.Accordion;
import com.vaadin.flow.component.accordion.AccordionPanel;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "help", layout = MainLayout.class)
@PermitAll
public class HelpView extends VerticalLayout implements HasDynamicTitle {

    @Override
    public String getPageTitle() {
        return getTranslation("help.title") + " | " + getTranslation("app.name");
    }


    public HelpView() {
        setWidthFull();
        setAlignItems(Alignment.CENTER);
        setPadding(false);
        setSpacing(false);
        getStyle().set("background", "var(--lumo-contrast-5pct)").set("padding", "var(--lumo-space-m)").set("overflow-y", "auto");

        Span title = new Span(getTranslation("help.title"));
        title.getStyle()
                .set("font-size", "var(--lumo-font-size-xxl)").set("font-weight", "700")
                .set("color", "var(--lumo-header-text-color)").set("align-self", "flex-start")
                .set("max-width", "860px").set("width", "100%");
        add(title);

        Accordion accordion = new Accordion();
        accordion.setWidthFull();
        accordion.getStyle().set("border-radius", "16px").set("overflow", "hidden");

        // Dashboard
        accordion.add(createHelpPanel(
            getTranslation("nav.dashboard"),
            getTranslation("help.dashboard")
        ));

        // Transaction History
        accordion.add(createHelpPanel(
            getTranslation("nav.transactions"),
            getTranslation("help.transactions")
        ));

        // Scheduled Transactions
        accordion.add(createHelpPanel(
            getTranslation("nav.scheduled"),
            getTranslation("help.scheduled")
        ));

        // Statistics
        accordion.add(createHelpPanel(
            getTranslation("nav.statistics"),
            getTranslation("help.statistics")
        ));

        // Vehicles
        accordion.add(createHelpPanel(
            getTranslation("nav.vehicles"),
            getTranslation("help.vehicles")
        ));

        // Manage Accounts
        accordion.add(createHelpPanel(
            getTranslation("nav.manage_accounts"),
            getTranslation("help.manage_accounts")
        ));

        // Payees
        accordion.add(createHelpPanel(
            getTranslation("nav.payees"),
            getTranslation("help.payees")
        ));

        // Categories
        accordion.add(createHelpPanel(
            getTranslation("nav.categories"),
            getTranslation("help.categories")
        ));

        // Tags
        accordion.add(createHelpPanel(
            getTranslation("nav.tags"),
            getTranslation("help.tags")
        ));

        // Currencies
        accordion.add(createHelpPanel(
            getTranslation("nav.currencies"),
            getTranslation("help.currencies")
        ));

        // Assets
        accordion.add(createHelpPanel(
            getTranslation("nav.assets"),
            getTranslation("help.assets")
        ));

        // Settings - User
        accordion.add(createHelpPanel(
            getTranslation("settings.user_title"),
            getTranslation("help.settings_user")
        ));

        // Settings - Import/Export
        accordion.add(createHelpPanel(
            getTranslation("settings.import_export_title"),
            getTranslation("help.settings_import")
        ));

        Div card = new Div(accordion);
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "20px")
                .set("box-shadow", "0 2px 12px rgba(0,0,0,0.06)")
                .set("overflow", "hidden")
                .set("max-width", "860px").set("width", "100%");
        add(card);
    }

    private AccordionPanel createHelpPanel(String title, String content) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);

        Paragraph description = new Paragraph(content);
        layout.add(description);

        AccordionPanel panel = new AccordionPanel(title, layout);
        return panel;
    }
}
