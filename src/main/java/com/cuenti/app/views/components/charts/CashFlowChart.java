package com.cuenti.app.views.components.charts;

import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.html.Div;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Server-rendered SVG grouped bar chart: income vs expense per time bucket.
 * Colors come from the validated chart tokens in the theme (light/dark via
 * CSS light-dark()); native SVG titles provide per-bar tooltips.
 */
public class CashFlowChart extends Div {

    private static final int CHART_H = 180;
    private static final int TOP_PAD = 8;
    private static final int BOTTOM_PAD = 22;
    private static final int BAR_W = 12;
    private static final int BAR_GAP = 2;       // 2px surface gap between paired bars
    private static final int GROUP_PAD = 12;
    private static final int LEFT_PAD = 44;

    /**
     * @param data     ordered bucket label → [income, expense]
     * @param format   currency formatter for tooltips and axis labels
     */
    public CashFlowChart(Map<String, BigDecimal[]> data, Function<BigDecimal, String> format) {
        getStyle().set("overflow-x", "auto").set("width", "100%");

        BigDecimal max = BigDecimal.ONE;
        for (BigDecimal[] v : data.values()) {
            max = max.max(v[0]).max(v[1]);
        }

        int groupW = BAR_W * 2 + BAR_GAP + GROUP_PAD;
        int width = LEFT_PAD + data.size() * groupW + 8;
        int height = TOP_PAD + CHART_H + BOTTOM_PAD;
        int baseline = TOP_PAD + CHART_H;

        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 ").append(width).append(' ').append(height)
           .append("' width='").append(width).append("' height='").append(height)
           .append("' role='img' style='display:block;min-width:100%'>");

        // horizontal hairline grid at 0/25/50/75/100%
        for (int i = 0; i <= 4; i++) {
            double y = baseline - (CHART_H * i / 4.0);
            svg.append("<line x1='").append(LEFT_PAD).append("' x2='").append(width)
               .append("' y1='").append(fmt(y)).append("' y2='").append(fmt(y))
               .append("' stroke='var(--cuenti-chart-grid)' stroke-width='1'/>");
            if (i > 0) {
                BigDecimal tick = max.multiply(BigDecimal.valueOf(i)).divide(BigDecimal.valueOf(4), 0, RoundingMode.HALF_UP);
                svg.append("<text x='").append(LEFT_PAD - 6).append("' y='").append(fmt(y + 3))
                   .append("' text-anchor='end' font-size='9' fill='var(--cuenti-chart-axis-text)'>")
                   .append(compact(tick)).append("</text>");
            }
        }

        int idx = 0;
        int labelEvery = Math.max(1, data.size() / 12);
        for (Map.Entry<String, BigDecimal[]> e : new LinkedHashMap<>(data).entrySet()) {
            int gx = LEFT_PAD + idx * groupW + GROUP_PAD / 2;
            appendBar(svg, gx, baseline, e.getValue()[0], max,
                    "var(--cuenti-chart-income)", e.getKey(), format.apply(e.getValue()[0]));
            appendBar(svg, gx + BAR_W + BAR_GAP, baseline, e.getValue()[1], max,
                    "var(--cuenti-chart-expense)", e.getKey(), format.apply(e.getValue()[1]));

            if (idx % labelEvery == 0) {
                svg.append("<text x='").append(gx + BAR_W + BAR_GAP / 2).append("' y='").append(baseline + 14)
                   .append("' text-anchor='middle' font-size='9' fill='var(--cuenti-chart-axis-text)'>")
                   .append(escape(e.getKey())).append("</text>");
            }
            idx++;
        }

        // baseline
        svg.append("<line x1='").append(LEFT_PAD).append("' x2='").append(width)
           .append("' y1='").append(baseline).append("' y2='").append(baseline)
           .append("' stroke='var(--vaadin-border-color-secondary)' stroke-width='1'/>");

        svg.append("</svg>");
        add(new Html("<div>" + svg + "</div>"));
    }

    private void appendBar(StringBuilder svg, int x, int baseline, BigDecimal value,
                           BigDecimal max, String color, String bucket, String tooltip) {
        double h = max.compareTo(BigDecimal.ZERO) > 0
                ? value.divide(max, 6, RoundingMode.HALF_UP).doubleValue() * CHART_H
                : 0;
        h = Math.max(h, 1.5);
        double top = baseline - h;
        double r = Math.min(3, h); // rounded data end, square baseline
        String path = "M" + x + " " + fmt(baseline)
                + " V" + fmt(top + r)
                + " Q" + x + " " + fmt(top) + " " + fmt(x + r) + " " + fmt(top)
                + " H" + fmt(x + BAR_W - r)
                + " Q" + (x + BAR_W) + " " + fmt(top) + " " + (x + BAR_W) + " " + fmt(top + r)
                + " V" + fmt(baseline) + " Z";
        svg.append("<path d='").append(path).append("' fill='").append(color).append("'>")
           .append("<title>").append(escape(bucket)).append(": ").append(escape(tooltip)).append("</title></path>");
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format(java.util.Locale.ROOT, "%.1f", d);
    }

    private static String compact(BigDecimal v) {
        long n = v.longValue();
        if (Math.abs(n) >= 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fM", n / 1_000_000.0);
        if (Math.abs(n) >= 1_000)     return String.format(java.util.Locale.ROOT, "%.1fk", n / 1_000.0);
        return String.valueOf(n);
    }

    static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;");
    }
}
