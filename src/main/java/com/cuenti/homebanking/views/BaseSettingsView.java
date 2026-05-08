package com.cuenti.homebanking.views;

import com.cuenti.homebanking.model.User;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.service.UserService;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

abstract class BaseSettingsView extends VerticalLayout {

    protected final User currentUser;
    protected final Div container = new Div();

    protected BaseSettingsView(SecurityUtils securityUtils, UserService userService) {
        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new IllegalStateException("User not authenticated"));
        this.currentUser = userService.findByUsername(username);

        setWidthFull();
        setAlignItems(Alignment.CENTER);
        setPadding(false);
        setSpacing(false);
        getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("padding", "var(--lumo-space-m)")
                .set("overflow-y", "auto");

        container.setWidthFull();
        container.setMaxWidth("860px");
        container.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "var(--lumo-space-m)");

        add(container);
    }

    protected Div createCard() {
        Div card = new Div();
        card.setWidthFull();
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "20px")
                .set("padding", "var(--lumo-space-l)")
                .set("box-shadow", "0 2px 12px rgba(0,0,0,0.06)")
                .set("box-sizing", "border-box")
                .set("display", "flex").set("flex-direction", "column")
                .set("gap", "var(--lumo-space-m)");
        return card;
    }

    protected Div cardHeader(VaadinIcon icon, String title, String subtitle, String accentColor) {
        Icon ico = icon.create();
        ico.getStyle()
                .set("color", accentColor != null ? accentColor : "var(--lumo-primary-color)")
                .set("font-size", "20px").set("flex-shrink", "0");

        Span titleSpan = new Span(title);
        titleSpan.getStyle()
                .set("font-size", "var(--lumo-font-size-l)").set("font-weight", "700")
                .set("color", "var(--lumo-header-text-color)");

        Div headingRow = new Div();
        headingRow.getStyle().set("display", "flex").set("align-items", "center").set("gap", "var(--lumo-space-s)");
        headingRow.add(ico, titleSpan);

        Div wrapper = new Div();
        wrapper.setWidthFull();
        wrapper.getStyle()
                .set("padding-bottom", "var(--lumo-space-m)")
                .set("border-bottom", "1px solid var(--lumo-contrast-10pct)")
                .set("display", "flex").set("flex-direction", "column").set("gap", "4px");
        wrapper.add(headingRow);

        if (subtitle != null && !subtitle.isBlank()) {
            Span sub = new Span(subtitle);
            sub.getStyle().set("font-size", "var(--lumo-font-size-s)")
                    .set("color", "var(--lumo-secondary-text-color)");
            wrapper.add(sub);
        }
        return wrapper;
    }

    protected Div infoBanner(String text, boolean error) {
        Div banner = new Div();
        banner.setWidthFull();
        String color = error ? "var(--lumo-error-color)" : "var(--lumo-primary-color)";
        String bg    = error ? "rgba(var(--lumo-error-color-50pct-rgb, 255,63,63),0.08)"
                             : "var(--lumo-primary-color-10pct, rgba(26,119,242,0.08))";
        Span text_ = new Span(text);
        text_.getStyle().set("font-size", "var(--lumo-font-size-s)").set("color", color);
        banner.add(text_);
        banner.getStyle()
                .set("padding", "var(--lumo-space-m)")
                .set("border-radius", "10px")
                .set("background", bg)
                .set("border-left", "3px solid " + color)
                .set("box-sizing", "border-box");
        return banner;
    }
}
