package com.cuenti.app.views.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * Right-hand detail/edit panel in the StarPass style: fixed to the
 * viewport edge, header with title/subtitle/status pill and close button,
 * scrolling content, pinned footer for actions. Closes on Escape.
 */
public class DetailPanel extends Div {

    private final Span title = new Span();
    private final Span subtitle = new Span();
    private final Div pillSlot = new Div();
    private final Div content = new Div();
    private final Div footer = new Div();
    private Runnable closeCallback = () -> {};

    public DetailPanel() {
        addClassName("detail-panel");
        setVisible(false);

        title.addClassName("detail-title");
        subtitle.addClassName("detail-subtitle");
        pillSlot.addClassName("detail-pill-slot");

        Button close = new Button(VaadinIcon.CLOSE.create(), e -> closePanel());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        close.getElement().setAttribute("aria-label", "Close");

        Div titles = new Div(title, subtitle);
        titles.addClassName("detail-titles");

        Div header = new Div(titles, pillSlot, close);
        header.addClassName("detail-header");

        content.addClassName("detail-content");
        footer.addClassName("detail-footer");

        add(header, content, footer);

        Shortcuts.addShortcutListener(this, this::closePanel, Key.ESCAPE);
    }

    public void setHeader(String titleText, String subtitleText) {
        title.setText(titleText);
        subtitle.setText(subtitleText != null ? subtitleText : "");
        subtitle.setVisible(subtitleText != null && !subtitleText.isBlank());
    }

    public void setPill(Component pill) {
        pillSlot.removeAll();
        if (pill != null) {
            pillSlot.add(pill);
        }
    }

    public Div content() {
        return content;
    }

    public Div footer() {
        return footer;
    }

    public void openPanel() {
        setVisible(true);
    }

    public void closePanel() {
        setVisible(false);
        closeCallback.run();
    }

    /** Called whenever the panel closes (X, Escape). */
    public void setCloseCallback(Runnable callback) {
        this.closeCallback = callback != null ? callback : () -> {};
    }
}
