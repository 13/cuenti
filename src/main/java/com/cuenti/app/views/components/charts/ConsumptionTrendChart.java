package com.cuenti.app.views.components.charts;

import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.html.Div;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Server-rendered SVG line chart: fuel consumption (L/100km) per fill-up over
 * time. Time-proportional x-axis, padded y-range so variation stays visible,
 * dashed reference line at the period average. Colors come from the validated
 * chart tokens in the theme (light/dark via CSS light-dark()); native SVG
 * titles provide per-point tooltips.
 */
public class ConsumptionTrendChart extends Div {

    /** One fill-up with a computed consumption value; tooltip is prebuilt by the caller. */
    public record Point(LocalDate date, BigDecimal consumption, String tooltip) {}

    private static final int WIDTH = 720;
    private static final int HEIGHT = 160;
    private static final int TOP_PAD = 10;
    private static final int BOTTOM_PAD = 22;
    private static final int LEFT_PAD = 34;
    private static final int RIGHT_PAD = 48;   // room for the Ø label
    private static final int MAX_DATE_LABELS = 6;

    private final String svg;

    public ConsumptionTrendChart(List<Point> points, BigDecimal average, Locale locale) {
        getStyle().set("width", "100%");
        this.svg = buildSvg(points, average, locale);
        add(new Html("<div>" + svg + "</div>"));
    }

    /** The raw SVG markup; exposed for tests. */
    public String getSvg() {
        return svg;
    }

    static String buildSvg(List<Point> points, BigDecimal average, Locale locale) {
        double left = LEFT_PAD;
        double right = WIDTH - RIGHT_PAD;
        double top = TOP_PAD;
        double bottom = HEIGHT - BOTTOM_PAD;

        double min = points.stream().mapToDouble(p -> p.consumption().doubleValue()).min().orElse(0);
        double max = points.stream().mapToDouble(p -> p.consumption().doubleValue()).max().orElse(0);
        if (average != null) {
            min = Math.min(min, average.doubleValue());
            max = Math.max(max, average.doubleValue());
        }
        double span = max - min;
        double pad = span == 0 ? Math.max(1, Math.abs(max) * 0.1) : span * 0.1;
        double yMin = min - pad;
        double yMax = max + pad;

        long minDay = points.stream().mapToLong(p -> p.date().toEpochDay()).min().orElse(0);
        long maxDay = points.stream().mapToLong(p -> p.date().toEpochDay()).max().orElse(0);
        long daySpan = maxDay - minDay;

        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 ").append(WIDTH).append(' ').append(HEIGHT)
           .append("' role='img' style='display:block;width:100%;height:auto'>");

        // horizontal hairline grid with y tick labels
        for (int i = 0; i <= 3; i++) {
            double value = yMin + (yMax - yMin) * i / 3.0;
            double y = y(value, yMin, yMax, top, bottom);
            svg.append("<line x1='").append(LEFT_PAD).append("' x2='").append(fmt(right))
               .append("' y1='").append(fmt(y)).append("' y2='").append(fmt(y))
               .append("' stroke='var(--cuenti-chart-grid)' stroke-width='1'/>")
               .append("<text x='").append(LEFT_PAD - 6).append("' y='").append(fmt(y + 3))
               .append("' text-anchor='end' font-size='9' fill='var(--cuenti-chart-axis-text)'>")
               .append(String.format(Locale.ROOT, "%.1f", value)).append("</text>");
        }

        // sparse date labels
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern(daySpan <= 120 ? "dd.MM" : "MMM", locale);
        int step = Math.max(1, (int) Math.ceil(points.size() / (double) MAX_DATE_LABELS));
        for (int i = 0; i < points.size(); i += step) {
            Point p = points.get(i);
            svg.append("<text x='").append(fmt(x(p, minDay, daySpan, left, right)))
               .append("' y='").append(HEIGHT - 8)
               .append("' text-anchor='middle' font-size='9' fill='var(--cuenti-chart-axis-text)'>")
               .append(CashFlowChart.escape(p.date().format(dateFmt))).append("</text>");
        }

        // average reference line
        if (average != null) {
            double y = y(average.doubleValue(), yMin, yMax, top, bottom);
            svg.append("<line x1='").append(LEFT_PAD).append("' x2='").append(fmt(right))
               .append("' y1='").append(fmt(y)).append("' y2='").append(fmt(y))
               .append("' stroke='var(--cuenti-chart-consumption)' stroke-width='1' stroke-dasharray='4 3' opacity='0.8'/>")
               .append("<text x='").append(fmt(right + 4)).append("' y='").append(fmt(y + 3))
               .append("' text-anchor='start' font-size='10' font-weight='700' fill='var(--cuenti-chart-consumption)'>")
               .append("Ø ").append(average.toPlainString()).append("</text>");
        }

        // series line
        if (points.size() >= 2) {
            svg.append("<polyline fill='none' stroke='var(--cuenti-chart-consumption)' stroke-width='2' ")
               .append("stroke-linejoin='round' stroke-linecap='round' points='");
            for (Point p : points) {
                svg.append(fmt(x(p, minDay, daySpan, left, right))).append(',')
                   .append(fmt(y(p.consumption().doubleValue(), yMin, yMax, top, bottom))).append(' ');
            }
            svg.append("'/>");
        }

        // data points with tooltips
        for (Point p : points) {
            svg.append("<circle cx='").append(fmt(x(p, minDay, daySpan, left, right)))
               .append("' cy='").append(fmt(y(p.consumption().doubleValue(), yMin, yMax, top, bottom)))
               .append("' r='3.5' fill='var(--cuenti-chart-consumption)'>")
               .append("<title>").append(CashFlowChart.escape(p.tooltip())).append("</title></circle>");
        }

        svg.append("</svg>");
        return svg.toString();
    }

    private static double x(Point p, long minDay, long daySpan, double left, double right) {
        if (daySpan == 0) return (left + right) / 2;
        return left + (p.date().toEpochDay() - minDay) / (double) daySpan * (right - left);
    }

    private static double y(double value, double yMin, double yMax, double top, double bottom) {
        return bottom - (value - yMin) / (yMax - yMin) * (bottom - top);
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format(Locale.ROOT, "%.1f", d);
    }
}
