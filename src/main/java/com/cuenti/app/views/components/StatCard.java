package com.cuenti.app.views.components;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * Dashboard metric tile: uppercase label with icon, large tabular value,
 * optional caption. Styled by {@code stat-card} classes in the cuenti theme.
 */
public class StatCard extends Div {

    private final Span value = new Span();
    private final Span caption = new Span();

    public StatCard(VaadinIcon icon, String label, String initialValue) {
        addClassNames("card", "stat-card");

        Span labelSpan = new Span();
        labelSpan.addClassName("stat-label");
        if (icon != null) {
            Icon ico = icon.create();
            labelSpan.add(ico);
        }
        labelSpan.add(new Span(label));

        value.addClassName("stat-value");
        value.setText(initialValue);

        caption.addClassName("stat-caption");
        caption.setVisible(false);

        add(labelSpan, value, caption);
    }

    public void setValue(String text) {
        value.setText(text);
    }

    /** Colors the value using the semantic amount classes. */
    public void setTrend(boolean positive) {
        value.removeClassNames("amount-positive", "amount-negative");
        value.addClassName(positive ? "amount-positive" : "amount-negative");
    }

    public void setCaption(String text) {
        caption.setText(text);
        caption.setVisible(text != null && !text.isEmpty());
    }
}
