package com.cuenti.app.views.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * StarPass-style detail field: rounded icon tile, small gray label,
 * bold value underneath.
 */
public class FieldRow extends Div {

    public FieldRow(VaadinIcon icon, String label, String value) {
        this(icon, label, new Span(value == null || value.isBlank() ? "—" : value));
    }

    public FieldRow(VaadinIcon icon, String label, Component value) {
        addClassName("field-row");

        Div tile = new Div(icon.create());
        tile.addClassName("field-tile");

        Span labelSpan = new Span(label);
        labelSpan.addClassName("field-label");

        value.getElement().getClassList().add("field-value");

        Div text = new Div(labelSpan, value);
        text.addClassName("field-text");

        add(tile, text);
    }
}
