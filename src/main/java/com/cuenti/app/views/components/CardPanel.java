package com.cuenti.app.views.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;

/**
 * Standard content card with optional title. Visuals come from the
 * {@code card} / {@code card-title} classes in the cuenti theme.
 */
public class CardPanel extends Div {

    public CardPanel(Component... content) {
        addClassName("card");
        add(content);
    }

    public CardPanel(String title, Component... content) {
        addClassName("card");
        H3 heading = new H3(title);
        heading.addClassName("card-title");
        add(heading);
        add(content);
    }
}
