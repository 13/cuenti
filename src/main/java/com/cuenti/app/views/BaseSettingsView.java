package com.cuenti.app.views;

import com.cuenti.app.model.User;
import com.cuenti.app.security.SecurityUtils;
import com.cuenti.app.service.UserService;
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

        addClassName("page-scroll");
        setWidthFull();
        setAlignItems(Alignment.CENTER);
        setPadding(false);
        setSpacing(false);
        getStyle().set("padding", "var(--vaadin-padding-m)");

        container.addClassNames("page-container", "page-container--narrow");
        container.getStyle().set("padding", "0");

        add(container);
    }

    protected Div createCard() {
        Div card = new Div();
        card.setWidthFull();
        card.addClassNames("card", "card--flex");
        return card;
    }

    protected Div cardHeader(VaadinIcon icon, String title, String subtitle, String accentColor) {
        Icon ico = icon.create();
        ico.getStyle()
                .set("color", accentColor != null ? accentColor : "var(--aura-accent-color)")
                .set("font-size", "20px").set("flex-shrink", "0");

        Span titleSpan = new Span(title);
        titleSpan.getStyle()
                .set("font-size", "var(--aura-font-size-l)").set("font-weight", "700")
                .set("color", "var(--vaadin-text-color)");

        Div headingRow = new Div();
        headingRow.getStyle().set("display", "flex").set("align-items", "center").set("gap", "var(--vaadin-gap-s)");
        headingRow.add(ico, titleSpan);

        Div wrapper = new Div();
        wrapper.setWidthFull();
        wrapper.getStyle()
                .set("padding-bottom", "var(--vaadin-gap-s)")
                .set("border-bottom", "1px solid var(--cuenti-divider)")
                .set("display", "flex").set("flex-direction", "column").set("gap", "4px");
        wrapper.add(headingRow);

        if (subtitle != null && !subtitle.isBlank()) {
            Span sub = new Span(subtitle);
            sub.getStyle().set("font-size", "var(--aura-font-size-s)")
                    .set("color", "var(--vaadin-text-color-secondary)");
            wrapper.add(sub);
        }
        return wrapper;
    }

    protected Div infoBanner(String text, boolean error) {
        Div banner = new Div();
        banner.setWidthFull();
        String color = error ? "var(--aura-red)" : "var(--aura-accent-color)";
        String bg    = error ? "color-mix(in srgb, var(--aura-red) 8%, transparent)"
                             : "var(--aura-accent-surface)";
        Span text_ = new Span(text);
        text_.getStyle().set("font-size", "var(--aura-font-size-s)").set("color", color);
        banner.add(text_);
        banner.getStyle()
                .set("padding", "var(--vaadin-padding-m)")
                .set("border-radius", "var(--vaadin-radius-m)")
                .set("background", bg)
                .set("border-left", "3px solid " + color)
                .set("box-sizing", "border-box");
        return banner;
    }
}
