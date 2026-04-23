package com.cuenti.homebanking.views;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
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
@PageTitle("About")
@PermitAll
public class AboutView extends VerticalLayout {

    private final BuildProperties buildProperties;

    public AboutView(DataSource dataSource, ObjectProvider<BuildProperties> buildPropertiesProvider) {
        this.buildProperties = buildPropertiesProvider.getIfAvailable();
        setSpacing(true);
        setPadding(true);
        setMaxWidth("800px");
        setAlignItems(Alignment.CENTER);
        getStyle().set("margin", "0 auto");

        // Logo
        Image logo = new Image("images/Cuenti.png", "Cuenti");
        logo.setMaxWidth("200px");
        logo.getStyle().set("margin-bottom", "var(--lumo-space-l)");
        add(logo);

        // Application Name
        H2 appName = new H2("Cuenti Homebanking");
        appName.getStyle()
            .set("margin-top", "0")
            .set("color", "var(--lumo-primary-text-color)");
        add(appName);

        // Version information from pom.xml
        String version = getVersion();
        String buildDate = getBuildDate();

        Div versionInfo = new Div();
        versionInfo.getStyle()
            .set("text-align", "center")
            .set("margin-bottom", "var(--lumo-space-l)");

        H3 versionTitle = new H3("Version " + version);
        versionTitle.getStyle().set("margin", "var(--lumo-space-s) 0");

        Paragraph buildInfo = new Paragraph("Built on: " + buildDate);
        buildInfo.getStyle()
            .set("margin", "var(--lumo-space-xs) 0")
            .set("color", "var(--lumo-secondary-text-color)");

        versionInfo.add(versionTitle, buildInfo);
        add(versionInfo);

        // Description
        Paragraph description = new Paragraph(
            "Cuenti is a comprehensive personal finance management application that helps you " +
            "track your income, expenses, assets, and investments all in one place."
        );
        description.getStyle()
            .set("text-align", "center")
            .set("max-width", "600px")
            .set("margin-bottom", "var(--lumo-space-l)");
        add(description);

        // Technology Stack
        Div techStack = new Div();
        techStack.getStyle().set("text-align", "center");

        H3 techTitle = new H3("Technology Stack");
        techTitle.getStyle().set("margin-bottom", "var(--lumo-space-s)");

        Paragraph techInfo = new Paragraph();
        techInfo.add(createInfoLine("Framework: Spring Boot " + SpringBootVersion.getVersion()));
        techInfo.add(createInfoLine("UI Framework: Vaadin " + Version.getFullVersion()));
        techInfo.add(createInfoLine("Java Version: " + System.getProperty("java.version")));
        techInfo.add(createInfoLine("Database: " + getDatabaseInfo(dataSource)));
        techInfo.getStyle()
            .set("color", "var(--lumo-secondary-text-color)")
            .set("font-size", "var(--lumo-font-size-s)");

        techStack.add(techTitle, techInfo);
        add(techStack);

        // Author/Copyright
        Div copyright = new Div();
        copyright.getStyle()
            .set("text-align", "center")
            .set("margin-top", "var(--lumo-space-xl)")
            .set("padding-top", "var(--lumo-space-l)")
            .set("border-top", "1px solid var(--lumo-contrast-10pct)")
            .set("color", "var(--lumo-secondary-text-color)")
            .set("font-size", "var(--lumo-font-size-s)");

        int currentYear = LocalDate.now().getYear();
        Paragraph copyrightText = new Paragraph("© " + currentYear + " Cuenti. All rights reserved.");
        copyrightText.getStyle().set("margin", "0");

        copyright.add(copyrightText);
        add(copyright);
    }

    private String getDatabaseInfo(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            return metaData.getDatabaseProductName() + " " + metaData.getDatabaseProductVersion();
        } catch (Exception e) {
            return "Unknown Database";
        }
    }

    private Div createInfoLine(String text) {
        Div line = new Div();
        line.setText(text);
        line.getStyle().set("margin", "var(--lumo-space-xs) 0");
        return line;
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
