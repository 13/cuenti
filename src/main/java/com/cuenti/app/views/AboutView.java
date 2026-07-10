package com.cuenti.app.views;

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
        addClassName("page-scroll");
        getStyle().set("padding", "var(--vaadin-padding-m)");

        String version   = getVersion();
        String buildDate = getBuildDate();
        int currentYear  = LocalDate.now().getYear();

        // ── Outer card ─────────────────────────────────────────────
        Div card = new Div();
        card.addClassName("card");
        card.getStyle()
                .set("padding", "var(--cuenti-space-xl)")
                .set("max-width", "680px").set("width", "100%")
                .set("display", "flex").set("flex-direction", "column")
                .set("align-items", "center").set("gap", "var(--vaadin-gap-l)")
                .set("box-sizing", "border-box");

        // Logo + app name
        Image logo = new Image("images/Cuenti.png", getTranslation("app.name"));
        logo.setWidth("120px"); logo.setHeight("auto");

        Span appName = new Span(getTranslation("app.name"));
        appName.getStyle()
                .set("font-size", "var(--cuenti-font-size-xxl)").set("font-weight", "800")
                .set("color", "var(--vaadin-text-color)");

        Span tagline = new Span(getTranslation("app.name") + " — " + getTranslation("about.tagline", "Personal Finance, Simplified"));
        tagline.getStyle().set("font-size", "var(--aura-font-size-s)").set("color", "var(--vaadin-text-color-secondary)");

        Div heroSection = new Div(logo, appName, tagline);
        heroSection.getStyle().set("display","flex").set("flex-direction","column").set("align-items","center").set("gap","var(--vaadin-gap-s)");
        card.add(heroSection);

        // Version badge
        Div versionBadge = new Div();
        versionBadge.getStyle()
                .set("display","flex").set("gap","var(--vaadin-gap-m)").set("flex-wrap","wrap").set("justify-content","center");
        versionBadge.add(infoPill("v" + version, "var(--aura-accent-color)"));
        versionBadge.add(infoPill(buildDate, "var(--vaadin-text-color-secondary)"));
        card.add(versionBadge);

        // Tech stack
        Div techCard = new Div();
        techCard.setWidthFull();
        techCard.getStyle()
                .set("background","var(--cuenti-surface-muted)").set("border-radius","var(--vaadin-radius-l)")
                .set("padding","var(--vaadin-gap-m) var(--vaadin-gap-l)")
                .set("display","flex").set("flex-direction","column").set("gap","var(--vaadin-gap-xs)");
        Span techTitle = new Span(getTranslation("about.tech_title").toUpperCase());
        techTitle.getStyle().set("font-size","10px").set("font-weight","700").set("letter-spacing","0.08em").set("color","var(--vaadin-text-color-secondary)");
        techCard.add(techTitle);
        String[][] rows = new String[][]{
                { getTranslation("tech.spring_boot"), SpringBootVersion.getVersion() },
                { getTranslation("tech.vaadin"), Version.getFullVersion() },
                { getTranslation("tech.java"), System.getProperty("java.version") },
                { getTranslation("tech.database"), getDatabaseInfo(dataSource) }
        };
        for (String[] row : rows) {
            Div rowDiv = new Div();
            rowDiv.getStyle().set("display","flex").set("justify-content","space-between").set("align-items","center")
                    .set("padding","var(--vaadin-gap-xs) 0").set("border-bottom","1px solid var(--cuenti-divider)");
            Span k = new Span(row[0]); k.getStyle().set("font-size","var(--aura-font-size-s)").set("color","var(--vaadin-text-color-secondary)");
            Span v = new Span(row[1]); v.getStyle().set("font-size","var(--aura-font-size-s)").set("font-weight","600");
            rowDiv.add(k, v);
            techCard.add(rowDiv);
        }
        card.add(techCard);

        // Copyright
        Span copyright = new Span(getTranslation("about.copyright", String.valueOf(currentYear), getTranslation("app.name")));
        copyright.getStyle().set("font-size","var(--aura-font-size-xs)").set("color","var(--vaadin-text-color-disabled)");
        card.add(copyright);

        add(card);
    }

    private String getDatabaseInfo(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            return metaData.getDatabaseProductName() + " " + metaData.getDatabaseProductVersion();
        } catch (Exception e) {
            return getTranslation("error.unknown_database");
        }
    }

    private Span infoPill(String text, String color) {
        Span s = new Span(text);
        s.getStyle().set("font-size","var(--aura-font-size-xs)").set("font-weight","600")
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
