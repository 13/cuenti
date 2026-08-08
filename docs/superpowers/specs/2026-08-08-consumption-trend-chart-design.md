# Consumption Trend Chart (Verbrauchstrend) Redesign

**Date:** 2026-08-08
**Status:** Approved (design), pending implementation

## Problem

The Verbrauchstrend section on the vehicles page (`VehiclesView.renderConsumptionChart`)
renders one styled `Div` bar per fill-up with a value label above and a date label below.
Problems:

- **Visual quality:** crude div bars with inline gradients; no grid, no axis. Inconsistent
  with the server-rendered SVG charts used elsewhere (`CashFlowChart`, `DonutChart`).
- **Missing insight:** no average reference line; bars are evenly spaced regardless of
  date gaps, so the time dimension is distorted.
- **Scaling:** each bar reserves ≥36px, so long periods force horizontal scrolling and
  the per-bar value labels become clutter.

## Solution

Replace the div-bar chart with a reusable server-rendered SVG line chart component,
following the existing `CashFlowChart` pattern.

### Component: `ConsumptionTrendChart`

- Location: `src/main/java/com/cuenti/app/views/components/charts/ConsumptionTrendChart.java`
- Extends `Div`, renders a single SVG string via `Html` (same approach as `CashFlowChart`).
- Constructor input:
  - ordered `List<Point>` where `Point` = (`LocalDate date`, `BigDecimal consumption`,
    `String tooltip`) — tooltip text prebuilt by the caller so the component stays
    domain-agnostic.
  - `BigDecimal average` — the attributed average from the summary calculation (same
    number shown on the "Ø Verbrauch" summary card), not the mean of the plotted points.

### Chart rendering

- Fixed virtual canvas: viewBox ~720×160, rendered at `width:100%` — fills the card
  width with no horizontal scrolling regardless of point count.
- **X-axis:** time-proportional; each point's x position maps linearly from its date
  within [minDate, maxDate]. Gaps between fill-ups are visible. Sparse date labels
  (target ~6 labels max), format `dd.MM` for spans ≤ ~120 days, `MMM` otherwise.
- **Y-axis:** padded min/max around the data range (not zero-based) so variation is
  visible. 4 horizontal grid lines with tick labels, using `--cuenti-chart-grid` and
  `--cuenti-chart-axis-text` tokens like `CashFlowChart`. The average line's value is
  included in the y-range so it is always visible.
- **Series:** polyline through all points plus a small circle per fill-up. Each circle
  carries a native SVG `<title>` tooltip (date, L/100km, liters, cost).
- **Average line:** dashed horizontal line at `average`, labeled `Ø <value>` at the
  right edge.
- **Color:** one new theme token `--cuenti-chart-consumption` (light-dark pair) in
  `themes/cuenti/styles.css`, used for line, dots, and average line. No per-point
  threshold coloring.

### Integration

- `VehiclesView.renderConsumptionChart` builds the point list (entries with non-null
  consumption, sorted by date — same filter as today), builds tooltips and passes the
  attributed average; the div-bar rendering code is deleted.
- Section title and placement unchanged.

### Edge cases

- 0 points with consumption: keep the existing `vehicles.no_consumption_data` notice
  (component not rendered).
- 1 point: render dot + average line, no polyline.
- All points same value: y-range padding prevents division by zero / flat-line collapse.

## Testing

- Vaadin Browserless test (pattern: existing `UC1xx` tests in
  `src/test/java/com/cuenti/app/views/`): after selecting a vehicle with fuel entries,
  the trend section contains a `ConsumptionTrendChart`; SVG markup contains the expected
  number of circles and the `Ø` average label. Empty-consumption case shows the notice.
- Unit-level check of x/y scaling edge cases (single point, identical values) if the
  math is extracted into package-private helpers.

## Out of scope

- Threshold coloring of points (green/orange/red) — explicitly not selected.
- Moving average overlay, trend direction badge, monthly aggregation.
- Changes to summary cards or fuel entry grid.
