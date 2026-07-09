package com.cuenti.app.views.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;

/**
 * Standard page header: title, optional subtitle, optional trailing actions.
 * Layout and typography come from the {@code page-header} / {@code page-title}
 * classes in the cuenti theme.
 */
public class PageHeader extends Div {

    private final Div textBlock = new Div();
    private final Div actions = new Div();

    public PageHeader(String title) {
        addClassName("page-header");

        H2 heading = new H2(title);
        heading.addClassName("page-title");
        textBlock.add(heading);

        actions.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("gap", "var(--lumo-space-s)")
                .set("flex-wrap", "wrap");

        add(textBlock, actions);
    }

    public PageHeader(String title, String subtitle) {
        this(title);
        setSubtitle(subtitle);
    }

    public void setSubtitle(String subtitle) {
        Span sub = new Span(subtitle);
        sub.addClassName("page-subtitle");
        textBlock.add(sub);
        textBlock.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "2px");
    }

    /** Adds components to the right-hand action area (buttons, selects, ...). */
    public void addAction(Component... components) {
        actions.add(components);
    }
}
