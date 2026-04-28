package com.cuenti.homebanking.views;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.Version;
import jakarta.annotation.security.PermitAll;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.info.BuildProperties;
import org.springframework.beans.factory.ObjectProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.ZoneId;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Route(value = "about", layout = MainLayout.class)
@PermitAll
public class AboutView extends VerticalLayout implements HasDynamicTitle {

    @Override
    public String getPageTitle() {
        return getTranslation("about.title") + " | " + getTranslation("app.name");
    }


    private final BuildProperties buildProperties;

    public AboutView(DataSource dataSource, ObjectProvider<BuildProperties> buildPropertiesProvider) {
        this.buildProperties = buildPropertiesProvider.getIfAvailable();
        setWidthFull();
        setAlignItems(Alignment.CENTER);
        setPadding(false);
        setSpacing(false);
        getStyle().set("background", "var(--lumo-contrast-5pct)").set("padding", "var(--lumo-space-l)").set("overflow-y", "auto");

        String version   = getVersion();
        String buildDate = getBuildDate();
        int currentYear  = LocalDate.now().getYear();

        // ── Outer card ─────────────────────────────────────────────
        Div card = new Div();
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "20px")
                .set("box-shadow", "0 2px 12px rgba(0,0,0,0.06)")
                .set("padding", "var(--lumo-space-xl)")
                .set("max-width", "680px").set("width", "100%")
                .set("display", "flex").set("flex-direction", "column")
                .set("align-items", "center").set("gap", "var(--lumo-space-l)")
                .set("box-sizing", "border-box");

        // Logo + app name
        Image logo = new Image("images/Cuenti.png", getTranslation("app.name"));
        logo.setWidth("120px"); logo.setHeight("auto");

        Span appName = new Span(getTranslation("app.name"));
        appName.getStyle()
                .set("font-size", "var(--lumo-font-size-xxl)").set("font-weight", "800")
                .set("color", "var(--lumo-header-text-color)");

        Span tagline = new Span(getTranslation("app.name") + " — " + getTranslation("about.tagline", "Personal Finance, Simplified"));
        tagline.getStyle().set("font-size", "var(--lumo-font-size-s)").set("color", "var(--lumo-secondary-text-color)");

        Div heroSection = new Div(logo, appName, tagline);
        heroSection.getStyle().set("display","flex").set("flex-direction","column").set("align-items","center").set("gap","var(--lumo-space-s)");
        card.add(heroSection);

        // Version badge
        Div versionBadge = new Div();
        versionBadge.getStyle()
                .set("display","flex").set("gap","var(--lumo-space-m)").set("flex-wrap","wrap").set("justify-content","center");
        versionBadge.add(infoPill("v" + version, "var(--lumo-primary-color)"));
        versionBadge.add(infoPill(buildDate, "var(--lumo-secondary-text-color)"));
        card.add(versionBadge);

        // Tech stack
        Div techCard = new Div();
        techCard.setWidthFull();
        techCard.getStyle()
                .set("background","var(--lumo-contrast-5pct)").set("border-radius","16px")
                .set("padding","var(--lumo-space-m) var(--lumo-space-l)")
                .set("display","flex").set("flex-direction","column").set("gap","var(--lumo-space-xs)");
        Span techTitle = new Span("Technology Stack".toUpperCase());
        techTitle.getStyle().set("font-size","10px").set("font-weight","700").set("letter-spacing","0.08em").set("color","var(--lumo-secondary-text-color)");
        techCard.add(techTitle);
        for (String[] row : new String[][]{
                {"Spring Boot", SpringBootVersion.getVersion()},
                {"Vaadin", Version.getFullVersion()},
                {"Java", System.getProperty("java.version")},
                {"Database", getDatabaseInfo(dataSource)}
        }) {
            Div rowDiv = new Div();
            rowDiv.getStyle().set("display","flex").set("justify-content","space-between").set("align-items","center")
                    .set("padding","var(--lumo-space-xs) 0").set("border-bottom","1px solid var(--lumo-contrast-5pct)");
            Span k = new Span(row[0]); k.getStyle().set("font-size","var(--lumo-font-size-s)").set("color","var(--lumo-secondary-text-color)");
            Span v = new Span(row[1]); v.getStyle().set("font-size","var(--lumo-font-size-s)").set("font-weight","600");
            rowDiv.add(k, v);
            techCard.add(rowDiv);
        }
        card.add(techCard);

        // Copyright
        Span copyright = new Span("© " + currentYear + " " + getTranslation("app.name") + ". All rights reserved.");
        copyright.getStyle().set("font-size","var(--lumo-font-size-xs)").set("color","var(--lumo-tertiary-text-color)");
        card.add(copyright);

        add(card);
    }

    private String getDatabaseInfo(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            return metaData.getDatabaseProductName() + " " + metaData.getDatabaseProductVersion();
        } catch (Exception e) {
            return "Unknown Database";
        }
    }

    private Span infoPill(String text, String color) {
        Span s = new Span(text);
        s.getStyle().set("font-size","var(--lumo-font-size-xs)").set("font-weight","600")
                .set("padding","3px 10px").set("border-radius","99px")
                .set("background", color + "1a").set("color", color);
        return s;
    }

    private String getVersion() {
        if (buildProperties == null) {
            return "unknown";
        }
        String version = buildProperties.getVersion();
        return (version != null && !version.isBlank()) ? version : "unknown";
    }

    private String getBuildDate() {
        if (buildProperties == null) {
            return "unknown";
        }
        if (buildProperties.getTime() != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy HH:mm:ss")
                    .withZone(ZoneId.systemDefault());
            return formatter.format(buildProperties.getTime());
        }
        return "unknown";
    }
}
