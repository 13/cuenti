package com.cuenti.app.views.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;

/**
 * Filter/action row placed above grids: wraps on small screens, supports a
 * flexible spacer to push primary actions right. Styled by {@code toolbar}
 * classes in the cuenti theme.
 */
public class ViewToolbar extends Div {

    public ViewToolbar(Component... components) {
        addClassName("toolbar");
        add(components);
    }

    /** Pushes everything added after it to the right edge. */
    public void addSpacer() {
        Div spacer = new Div();
        spacer.addClassName("toolbar-spacer");
        add(spacer);
    }
}
