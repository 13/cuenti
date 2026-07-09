package com.cuenti.app.views.components;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.page.Page;

import java.util.List;

/**
 * Hides secondary grid columns below a viewport width so data grids stay
 * readable on phones. Applies on attach and reacts to browser resizes.
 */
public final class ResponsiveGridColumns {

    private ResponsiveGridColumns() {}

    public static void hideBelow(int minWidthPx, Grid<?> grid, List<Grid.Column<?>> columns) {
        grid.addAttachListener(e -> {
            UI ui = e.getUI();
            Page page = ui.getPage();
            page.retrieveExtendedClientDetails(d ->
                    apply(d.getWindowInnerWidth(), minWidthPx, columns));
            page.addBrowserWindowResizeListener(re ->
                    apply(re.getWidth(), minWidthPx, columns));
        });
    }

    private static void apply(int windowWidth, int minWidthPx, List<Grid.Column<?>> columns) {
        boolean visible = windowWidth >= minWidthPx;
        columns.forEach(c -> c.setVisible(visible));
    }
}
