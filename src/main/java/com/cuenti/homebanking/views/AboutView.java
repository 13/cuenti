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

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

@Route(value = "about", layout = MainLayout.class)
@PageTitle("About")
@PermitAll
public class AboutView extends VerticalLayout {

    public AboutView(DataSource dataSource) {
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
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (input != null) {
                Properties prop = new Properties();
                prop.load(input);
                String version = prop.getProperty("application.version");
                if (version != null && !version.isEmpty()) {
                    return version;
                }
            }
        } catch (IOException e) {
            // Ignore
        }
        return "0.0.1"; // Fallback version
    }

    private String getBuildDate() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (input != null) {
                Properties prop = new Properties();
                prop.load(input);
                String timestamp = prop.getProperty("build.timestamp");
                if (timestamp != null && !timestamp.isEmpty()) {
                    try {
                        Instant instant = Instant.parse(timestamp);
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy HH:mm:ss")
                            .withZone(ZoneId.systemDefault());
                        return formatter.format(instant);
                    } catch (Exception e) {
                        return timestamp;
                    }
                }
            }
        } catch (IOException e) {
            // Ignore
        }

        // Return current date as fallback
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")
            .withZone(ZoneId.systemDefault());
        return formatter.format(Instant.now());
    }
}
