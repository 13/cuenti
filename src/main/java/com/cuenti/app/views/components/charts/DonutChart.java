package com.cuenti.app.views.components.charts;

import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.html.Div;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

/**
 * Server-rendered SVG donut for up to five categories (validated categorical
 * palette, 2px surface gaps between segments, center shows the total).
 * The accompanying category list acts as legend and table-view relief.
 */
public class DonutChart extends Div {

    public record Slice(String label, BigDecimal value) {}

    private static final int SIZE = 168;
    private static final double R = 62;
    private static final double STROKE = 22;
    private static final double GAP_DEG = 2.4; // ≈2px surface gap at this radius

    public DonutChart(List<Slice> slices, BigDecimal total, String centerLabel,
                      Function<BigDecimal, String> format) {
        double c = SIZE / 2.0;

        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 ").append(SIZE).append(' ').append(SIZE)
           .append("' width='").append(SIZE).append("' height='").append(SIZE)
           .append("' role='img' style='display:block'>");

        double angle = -90; // start at 12 o'clock
        for (int i = 0; i < slices.size() && i < 5; i++) {
            Slice s = slices.get(i);
            double share = total.compareTo(BigDecimal.ZERO) > 0
                    ? s.value().doubleValue() / total.doubleValue() : 0;
            double sweep = Math.max(0, share * 360 - GAP_DEG);
            if (sweep <= 0.2) { angle += share * 360; continue; }

            double a0 = Math.toRadians(angle + GAP_DEG / 2);
            double a1 = Math.toRadians(angle + GAP_DEG / 2 + sweep);
            double x0 = c + R * Math.cos(a0), y0 = c + R * Math.sin(a0);
            double x1 = c + R * Math.cos(a1), y1 = c + R * Math.sin(a1);
            int large = sweep > 180 ? 1 : 0;

            svg.append("<path d='M").append(f(x0)).append(' ').append(f(y0))
               .append(" A").append(f(R)).append(' ').append(f(R)).append(" 0 ").append(large).append(" 1 ")
               .append(f(x1)).append(' ').append(f(y1))
               .append("' fill='none' stroke='var(--cuenti-chart-cat-").append(i + 1)
               .append(")' stroke-width='").append(f(STROKE)).append("' stroke-linecap='butt'>")
               .append("<title>").append(CashFlowChart.escape(s.label())).append(": ")
               .append(CashFlowChart.escape(format.apply(s.value())))
               .append(String.format(java.util.Locale.ROOT, " (%.0f%%)", share * 100))
               .append("</title></path>");

            angle += share * 360;
        }

        // center total (text tokens, never series color)
        svg.append("<text x='").append(f(c)).append("' y='").append(f(c - 4))
           .append("' text-anchor='middle' font-size='15' font-weight='700' fill='var(--vaadin-text-color)'>")
           .append(CashFlowChart.escape(format.apply(total))).append("</text>");
        svg.append("<text x='").append(f(c)).append("' y='").append(f(c + 14))
           .append("' text-anchor='middle' font-size='9' fill='var(--cuenti-chart-axis-text)'>")
           .append(CashFlowChart.escape(centerLabel)).append("</text>");

        svg.append("</svg>");
        add(new Html("<div>" + svg + "</div>"));
        getStyle().set("flex-shrink", "0");
    }

    private static String f(double d) {
        return String.format(java.util.Locale.ROOT, "%.2f", d);
    }
}
