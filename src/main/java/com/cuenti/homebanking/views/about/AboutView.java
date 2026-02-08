package com.cuenti.homebanking.views.about;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.Version;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootVersion;
import org.vaadin.lineawesome.LineAwesomeIconUrl;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.LocalDate;

@PageTitle("About")
@Route("about")
@Menu(order = 99, icon = LineAwesomeIconUrl.INFO_CIRCLE_SOLID)
@PermitAll
public class AboutView extends VerticalLayout {

    public AboutView(DataSource dataSource,
                     @Value("${application.version:0.0.1}") String appVersion,
                     @Value("${build.timestamp:Unknown}") String buildDate) {

        setSpacing(true);
        setPadding(true);
        setMaxWidth("800px");

        // Title and version
        H2 title = new H2("Cuenti Homebanking");
        title.getStyle().set("margin-bottom", "0");

        Paragraph version = new Paragraph("Version " + appVersion);
        version.getStyle().set("margin-top", "var(--lumo-space-xs)");

        Paragraph built = new Paragraph("Built on: " + buildDate);

        // Description
        Paragraph description = new Paragraph(
            "Cuenti is a comprehensive personal finance management application that helps you " +
            "track your income, expenses, assets, and investments all in one place."
        );
        description.getStyle().set("margin-top", "var(--lumo-space-l)");

        // Technology Stack
        H3 techTitle = new H3("Technology Stack");
        techTitle.getStyle().set("margin-top", "var(--lumo-space-l)");

        String springBootVersion = SpringBootVersion.getVersion();
        String vaadinVersion = Version.getFullVersion();
        String javaVersion = System.getProperty("java.version");
        String databaseInfo = getDatabaseInfo(dataSource);

        Div techStack = new Div();
        techStack.getStyle().set("line-height", "1.8");
        techStack.add(createInfoLine("Framework", "Spring Boot " + springBootVersion));
        techStack.add(createInfoLine("UI Framework", "Vaadin " + vaadinVersion));
        techStack.add(createInfoLine("Java Version", javaVersion));
        techStack.add(createInfoLine("Database", databaseInfo));

        // Copyright
        String copyrightYears = getCopyrightYears();
        Paragraph copyright = new Paragraph("© " + copyrightYears + " Cuenti. All rights reserved.");
        copyright.getStyle().set("margin-top", "var(--lumo-space-xl)");
        copyright.getStyle().set("color", "var(--lumo-secondary-text-color)");

        add(title, version, built, description, techTitle, techStack, copyright);
    }

    private Div createInfoLine(String label, String value) {
        Div line = new Div();
        Span labelSpan = new Span(label + ": ");
        labelSpan.getStyle().set("font-weight", "bold");
        line.add(labelSpan, new Span(value));
        return line;
    }

    private String getDatabaseInfo(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String productName = metaData.getDatabaseProductName();
            // Extract major.minor version
            String version = metaData.getDatabaseMajorVersion() + "." + metaData.getDatabaseMinorVersion();
            return productName + " " + version;
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private String getCopyrightYears() {
        int startYear = 2026;
        int currentYear = LocalDate.now().getYear();
        if (currentYear <= startYear) {
            return String.valueOf(startYear);
        }
        return startYear + "-" + currentYear;
    }
}
