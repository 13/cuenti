package com.cuenti.homebanking.views.settings;

import com.cuenti.homebanking.data.Account;
import com.cuenti.homebanking.data.User;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.services.*;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import jakarta.annotation.security.PermitAll;
import org.vaadin.lineawesome.LineAwesomeIconUrl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Import/Export view for backing up and restoring user data.
 * Supports JSON, XHB (Homebank), and Trade Republic CSV formats.
 */
@PageTitle("Import / Export")
@Route("import-export")
@Menu(order = 11, icon = LineAwesomeIconUrl.FILE_EXPORT_SOLID)
@PermitAll
public class ImportExportView extends VerticalLayout {

    private final UserService userService;
    private final AccountService accountService;
    private final JsonExportImportService jsonExportImportService;
    private final XhbImportService xhbImportService;
    private final XhbExportService xhbExportService;
    private final TradeRepublicImportService tradeRepublicImportService;
    private final SecurityUtils securityUtils;
    private final User currentUser;

    public ImportExportView(UserService userService, AccountService accountService,
                            JsonExportImportService jsonExportImportService,
                            XhbImportService xhbImportService,
                            XhbExportService xhbExportService,
                            TradeRepublicImportService tradeRepublicImportService,
                            SecurityUtils securityUtils) {
        this.userService = userService;
        this.accountService = accountService;
        this.jsonExportImportService = jsonExportImportService;
        this.xhbImportService = xhbImportService;
        this.xhbExportService = xhbExportService;
        this.tradeRepublicImportService = tradeRepublicImportService;
        this.securityUtils = securityUtils;

        String username = securityUtils.getAuthenticatedUsername()
                .orElseThrow(() -> new IllegalStateException("User not authenticated"));
        this.currentUser = userService.findByUsername(username);

        setSpacing(true);
        setPadding(true);
        setMaxWidth("1200px");
        getStyle().set("margin", "0 auto");

        setupUI();
    }

    private void setupUI() {
        H2 title = new H2(getTranslation("settings.import_export_title"));

        add(title);
        add(createJsonExportSection());
        add(createJsonImportSection());
        add(createXhbSection());
        add(createTradeRepublicSection());
    }

    // ========== JSON EXPORT ==========
    private VerticalLayout createJsonExportSection() {
        VerticalLayout section = createSection(getTranslation("settings.export_json"));

        Paragraph description = new Paragraph(
                "Export all your data as a JSON file for backup. This includes accounts, transactions, " +
                "categories, payees, tags, assets, currencies, and scheduled transactions.");

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = String.format("cuenti_backup_%s_%s.json", currentUser.getUsername(), timestamp);

        StreamResource jsonResource = new StreamResource(filename, () -> {
            try {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                jsonExportImportService.exportUserData(currentUser, outputStream);
                return new ByteArrayInputStream(outputStream.toByteArray());
            } catch (Exception ex) {
                Notification.show("Export failed: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return new ByteArrayInputStream(new byte[0]);
            }
        });
        jsonResource.setContentType("application/json");

        Button exportButton = new Button("Download JSON Backup", VaadinIcon.DOWNLOAD.create());
        exportButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Anchor downloadLink = new Anchor(jsonResource, "");
        downloadLink.getElement().setAttribute("download", true);
        downloadLink.add(exportButton);

        section.add(description, downloadLink);
        return section;
    }

    // ========== JSON IMPORT ==========
    private VerticalLayout createJsonImportSection() {
        VerticalLayout section = createSection(getTranslation("settings.import_json"));

        Paragraph description = new Paragraph(
                "Import data from a previously exported JSON backup file. " +
                "This will add the imported data to your existing profile.");

        Paragraph warning = new Paragraph(
                "⚠️ Warning: Importing will ADD data to your existing profile. Duplicate entries may be created. " +
                "If you want to restore from a backup to a clean state, go to Settings → Danger Zone first.");
        warning.getStyle()
                .set("color", "var(--lumo-warning-text-color)")
                .set("background-color", "var(--lumo-contrast-10pct)")
                .set("padding", "var(--lumo-space-m)")
                .set("border-radius", "var(--lumo-border-radius-m)");

        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes("application/json", ".json");
        upload.setMaxFiles(1);
        upload.setMaxFileSize(50 * 1024 * 1024);
        upload.setUploadButton(new Button("Upload JSON File", VaadinIcon.UPLOAD.create()));

        upload.addSucceededListener(event -> {
            try {
                jsonExportImportService.importUserData(currentUser, buffer.getInputStream());
                Notification.show("Import successful! Refreshing...", 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                UI.getCurrent().getPage().executeJs("setTimeout(function() { location.reload(); }, 2000);");
            } catch (Exception ex) {
                Notification.show("Import failed: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        section.add(description, upload, warning);
        return section;
    }

    // ========== HOMEBANK XHB ==========
    private VerticalLayout createXhbSection() {
        VerticalLayout section = createSection("Homebank (.xhb)");

        Paragraph description = new Paragraph(
                "Import or export data in Homebank XHB format. This allows you to migrate from Homebank " +
                "or share data with Homebank users.");

        // XHB Import
        MemoryBuffer xhbBuffer = new MemoryBuffer();
        Upload xhbUpload = new Upload(xhbBuffer);
        xhbUpload.setAcceptedFileTypes(".xhb");
        xhbUpload.setMaxFiles(1);
        xhbUpload.setUploadButton(new Button("Import XHB File", VaadinIcon.UPLOAD.create()));

        xhbUpload.addSucceededListener(event -> {
            try {
                xhbImportService.importXhb(xhbBuffer.getInputStream(), currentUser);
                Notification.show("XHB import successful!", 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                UI.getCurrent().getPage().reload();
            } catch (Exception ex) {
                Notification.show("XHB import failed: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        // XHB Export
        StreamResource xhbResource = new StreamResource("cuenti_export.xhb", () -> {
            try {
                return new ByteArrayInputStream(xhbExportService.exportXhb(currentUser));
            } catch (Exception ex) {
                Notification.show("XHB export failed: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return new ByteArrayInputStream(new byte[0]);
            }
        });

        Button xhbExportButton = new Button("Download XHB File", VaadinIcon.DOWNLOAD.create());
        Anchor xhbDownloadLink = new Anchor(xhbResource, "");
        xhbDownloadLink.getElement().setAttribute("download", true);
        xhbDownloadLink.add(xhbExportButton);

        section.add(description, xhbUpload, xhbDownloadLink);
        return section;
    }

    // ========== TRADE REPUBLIC ==========
    private VerticalLayout createTradeRepublicSection() {
        VerticalLayout section = createSection("Trade Republic Import");

        Paragraph description = new Paragraph(
                "Import your Trade Republic CSV statement. You need to select target accounts " +
                "for cash transactions and asset transactions.");

        ComboBox<Account> cashAccountCombo = new ComboBox<>("Target Cash Account");
        cashAccountCombo.setItems(accountService.getAccountsByUser(currentUser));
        cashAccountCombo.setItemLabelGenerator(Account::getAccountName);
        cashAccountCombo.setWidthFull();
        cashAccountCombo.setPlaceholder("Select your cash/bank account");

        ComboBox<Account> assetAccountCombo = new ComboBox<>("Target Asset Account");
        assetAccountCombo.setItems(accountService.getAccountsByUser(currentUser));
        assetAccountCombo.setItemLabelGenerator(Account::getAccountName);
        assetAccountCombo.setWidthFull();
        assetAccountCombo.setPlaceholder("Select your investment/asset account");

        MemoryBuffer trBuffer = new MemoryBuffer();
        Upload trUpload = new Upload(trBuffer);
        trUpload.setAcceptedFileTypes(".csv");
        trUpload.setMaxFiles(1);
        trUpload.setUploadButton(new Button("Import Trade Republic CSV", VaadinIcon.FILE_TEXT.create()));

        trUpload.addSucceededListener(event -> {
            if (cashAccountCombo.isEmpty() || assetAccountCombo.isEmpty()) {
                Notification.show("Please select both target accounts first", 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            try {
                tradeRepublicImportService.importCsv(trBuffer.getInputStream(),
                        cashAccountCombo.getValue(), assetAccountCombo.getValue());
                Notification.show("Trade Republic import successful!", 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                UI.getCurrent().getPage().reload();
            } catch (Exception ex) {
                Notification.show("Import failed: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        section.add(description, cashAccountCombo, assetAccountCombo, trUpload);
        return section;
    }

    // ========== HELPER ==========
    private VerticalLayout createSection(String title) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(true);
        section.setSpacing(true);
        section.getStyle()
                .set("background-color", "var(--lumo-base-color)")
                .set("border-radius", "16px")
                .set("box-shadow", "var(--lumo-box-shadow-s)");

        H3 sectionTitle = new H3(title);
        sectionTitle.getStyle().set("margin-top", "0");
        section.add(sectionTitle);

        return section;
    }
}
