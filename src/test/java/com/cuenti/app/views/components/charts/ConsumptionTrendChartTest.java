package com.cuenti.app.views.components.charts;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ConsumptionTrendChartTest {

    private static ConsumptionTrendChart.Point pt(String date, String value) {
        return new ConsumptionTrendChart.Point(LocalDate.parse(date), new BigDecimal(value), "tip");
    }

    private static int count(String s, String needle) {
        return s.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    @Test
    void multiplePoints_renderPolylineCirclesAndAverageLine() {
        String svg = ConsumptionTrendChart.buildSvg(
                List.of(pt("2026-01-10", "7.50"), pt("2026-02-10", "8.00"), pt("2026-03-10", "6.80")),
                new BigDecimal("7.45"), Locale.GERMAN);

        assertThat(svg).contains("<polyline");
        assertThat(count(svg, "<circle")).isEqualTo(3);
        assertThat(svg).contains("Ø 7.45");
        assertThat(svg).contains("stroke-dasharray");
        assertThat(svg).contains("var(--cuenti-chart-consumption)");
        assertThat(svg).contains("var(--cuenti-chart-grid)");
    }

    @Test
    void singlePoint_rendersDotAndAverageButNoPolyline() {
        String svg = ConsumptionTrendChart.buildSvg(
                List.of(pt("2026-01-10", "7.50")), new BigDecimal("7.50"), Locale.GERMAN);

        assertThat(svg).doesNotContain("<polyline");
        assertThat(count(svg, "<circle")).isEqualTo(1);
        assertThat(svg).contains("Ø 7.50");
    }

    @Test
    void identicalValues_produceFiniteCoordinates() {
        String svg = ConsumptionTrendChart.buildSvg(
                List.of(pt("2026-01-10", "7.00"), pt("2026-02-10", "7.00")),
                new BigDecimal("7.00"), Locale.GERMAN);

        assertThat(svg).doesNotContain("NaN");
        assertThat(svg).doesNotContain("Infinity");
        assertThat(count(svg, "<circle")).isEqualTo(2);
    }

    @Test
    void nullAverage_omitsAverageLine() {
        String svg = ConsumptionTrendChart.buildSvg(
                List.of(pt("2026-01-10", "7.50"), pt("2026-02-10", "8.00")), null, Locale.GERMAN);

        assertThat(svg).doesNotContain("Ø");
        assertThat(svg).doesNotContain("stroke-dasharray");
    }

    @Test
    void tooltipsAreEscaped() {
        String svg = ConsumptionTrendChart.buildSvg(
                List.of(new ConsumptionTrendChart.Point(
                        LocalDate.parse("2026-01-10"), new BigDecimal("7.50"), "<b>&'x")),
                null, Locale.GERMAN);

        assertThat(svg).contains("&lt;b&gt;&amp;&#39;x");
        assertThat(svg).doesNotContain("<b>");
    }

    @Test
    void componentExposesSvgAndWrapsIt() {
        ConsumptionTrendChart chart = new ConsumptionTrendChart(
                List.of(pt("2026-01-10", "7.50")), new BigDecimal("7.50"), Locale.GERMAN);

        assertThat(chart.getSvg()).startsWith("<svg");
        assertThat(chart.getChildren().count()).isEqualTo(1);
    }
}
