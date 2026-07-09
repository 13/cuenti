package com.cuenti.app.views.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * Friendly placeholder when a list or chart has no data: icon, headline,
 * optional hint and call-to-action. Styled by {@code empty-state} classes.
 */
public class EmptyStateNotice extends Div {

    public EmptyStateNotice(VaadinIcon icon, String title, String hint) {
        addClassName("empty-state");
        if (icon != null) {
            add(icon.create());
        }
        Span titleSpan = new Span(title);
        titleSpan.addClassName("empty-state-title");
        add(titleSpan);
        if (hint != null && !hint.isEmpty()) {
            add(new Span(hint));
        }
    }

    public EmptyStateNotice(VaadinIcon icon, String title, String hint, Component action) {
        this(icon, title, hint);
        add(action);
    }
}
