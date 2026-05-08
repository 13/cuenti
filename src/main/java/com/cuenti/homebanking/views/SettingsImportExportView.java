package com.cuenti.homebanking.views;

import com.cuenti.homebanking.model.Account;
import com.cuenti.homebanking.security.SecurityUtils;
import com.cuenti.homebanking.service.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import com.vaadin.flow.server.streams.InMemoryUploadHandler;
import jakarta.annotation.security.PermitAll;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicReference;

@Route(value = "settings/import-export", layout = MainLayout.class)
@PermitAll
public class SettingsImportExportView extends BaseSettingsView implements HasDynamicTitle {

    private final AccountService accountService;
    private final XhbImportService xhbImportService;
    private final XhbExportService xhbExportService;
    private final TradeRepublicImportService tradeRepublicImportService;
    private final JsonExportImportService jsonExportImportService;

    public SettingsImportExportView(AccountService accountService,
                                    XhbImportService xhbImportService,
                                    XhbExportService xhbExportService,
                                    TradeRepublicImportService tradeRepublicImportService,
                                    JsonExportImportService jsonExportImportService,
                                    UserService userService,
                                    SecurityUtils securityUtils) {
        super(securityUtils, userService);
        this.accountService = accountService;
        this.xhbImportService = xhbImportService;
        this.xhbExportService = xhbExportService;
        this.tradeRepublicImportService = tradeRepublicImportService;
        this.jsonExportImportService = jsonExportImportService;
        buildContent();
    }

    @Override
    public String getPageTitle() {
        return getTranslation("settings.title") + " | " + getTranslation("app.name");
    }

    private void buildContent() {
        // ── Homebank XHB ──────────────────────────────────────────────
        Div card = createCard();
        card.add(cardHeader(VaadinIcon.DATABASE, getTranslation("settings.import_export_title"),
                getTranslation("settings.data_desc"), "var(--lumo-primary-color)"));

        AtomicReference<byte[]> xhbData = new AtomicReference<>();
        Upload upload = new Upload(new InMemoryUploadHandler((meta, bytes) -> xhbData.set(bytes)));
        upload.setAcceptedFileTypes(".xhb");
        upload.setUploadButton(new Button(getTranslation("settings.import"), VaadinIcon.UPLOAD.create()));
        upload.addAllFinishedListener(e -> {
            byte[] bytes = xhbData.getAndSet(null);
            if (bytes == null) return;
            try {
                xhbImportService.importXhb(new ByteArrayInputStream(bytes), currentUser);
                Notification.show(getTranslation("settings.import_success"), 2000, Notification.Position.BOTTOM_END).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                Notification.show(getTranslation("settings.import_failed", ex.getMessage()), 5000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        Button exportBtn = new Button(getTranslation("settings.export"), VaadinIcon.DOWNLOAD.create());
        exportBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Anchor anchor = new Anchor(DownloadHandler.fromInputStream(event -> {
            try {
                byte[] data = xhbExportService.exportXhb(currentUser);
                return new DownloadResponse(new ByteArrayInputStream(data), "export.xhb", "application/xml", data.length);
            } catch (Exception ex) {
                return DownloadResponse.error(500, ex.getMessage());
            }
        }), "");
        anchor.add(exportBtn);

        HorizontalLayout xhbActions = new HorizontalLayout(upload, anchor);
        xhbActions.setAlignItems(FlexComponent.Alignment.CENTER);
        xhbActions.setSpacing(false);
        xhbActions.getStyle().set("gap", "var(--lumo-space-m)").set("flex-wrap", "wrap");
        card.add(xhbActions);
        container.add(card);

        // ── Trade Republic ────────────────────────────────────────────
        Div trCard = createCard();
        trCard.add(cardHeader(VaadinIcon.STOCK, getTranslation("settings.tr_import_title"),
                getTranslation("settings.tr_desc"), "var(--lumo-success-color)"));

        ComboBox<Account> cashAccountCombo = new ComboBox<>(getTranslation("settings.tr_cash_account"));
        cashAccountCombo.setItems(accountService.getAccountsByUser(currentUser));
        cashAccountCombo.setItemLabelGenerator(Account::getAccountName);
        cashAccountCombo.setWidthFull();

        ComboBox<Account> assetAccountCombo = new ComboBox<>(getTranslation("settings.tr_asset_account"));
        assetAccountCombo.setItems(accountService.getAccountsByUser(currentUser));
        assetAccountCombo.setItemLabelGenerator(Account::getAccountName);
        assetAccountCombo.setWidthFull();

        HorizontalLayout trAccounts = new HorizontalLayout(cashAccountCombo, assetAccountCombo);
        trAccounts.setWidthFull(); trAccounts.setSpacing(false);
        trAccounts.getStyle().set("gap", "var(--lumo-space-m)").set("flex-wrap", "wrap");
        cashAccountCombo.getStyle().set("flex", "1 1 200px");
        assetAccountCombo.getStyle().set("flex", "1 1 200px");

        AtomicReference<byte[]> trData = new AtomicReference<>();
        Upload trUpload = new Upload(new InMemoryUploadHandler((meta, bytes) -> trData.set(bytes)));
        trUpload.setAcceptedFileTypes(".csv");
        trUpload.setUploadButton(new Button(getTranslation("settings.tr_import_btn"), VaadinIcon.FILE_TEXT.create()));
        trUpload.addAllFinishedListener(e -> {
            byte[] bytes = trData.getAndSet(null);
            if (bytes == null) return;
            if (cashAccountCombo.isEmpty() || assetAccountCombo.isEmpty()) {
                Notification.show(getTranslation("settings.tr_select_accounts"), 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            try {
                tradeRepublicImportService.importCsv(new ByteArrayInputStream(bytes), cashAccountCombo.getValue(), assetAccountCombo.getValue());
                Notification.show(getTranslation("settings.tr_success"), 2000, Notification.Position.BOTTOM_END).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                Notification.show(getTranslation("settings.import_failed", ex.getMessage()), 5000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        trCard.add(trAccounts, trUpload);
        container.add(trCard);

        // ── JSON Backup / Restore ─────────────────────────────────────
        Div jsonCard = createCard();
        jsonCard.add(cardHeader(VaadinIcon.ARCHIVE, getTranslation("settings.json_backup_restore"),
                getTranslation("settings.json_desc"), "var(--lumo-warning-color, #e8a000)"));

        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = String.format("cuenti_export_%s_%s.json", currentUser.getUsername(), timestamp);

        Button jsonExportBtn = new Button(getTranslation("settings.export_json"), VaadinIcon.DOWNLOAD.create());
        jsonExportBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Anchor jsonAnchor = new Anchor(DownloadHandler.fromInputStream(event -> {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                jsonExportImportService.exportUserData(currentUser, out);
                byte[] data = out.toByteArray();
                return new DownloadResponse(new ByteArrayInputStream(data), filename, "application/json", data.length);
            } catch (Exception ex) {
                return DownloadResponse.error(500, ex.getMessage());
            }
        }), "");
        jsonAnchor.add(jsonExportBtn);

        AtomicReference<byte[]> jsonData = new AtomicReference<>();
        Upload jsonUpload = new Upload(new InMemoryUploadHandler((meta, bytes) -> jsonData.set(bytes)));
        jsonUpload.setAcceptedFileTypes("application/json", ".json");
        jsonUpload.setUploadButton(new Button(getTranslation("settings.import_json"), VaadinIcon.UPLOAD.create()));
        jsonUpload.setMaxFiles(1);
        jsonUpload.setMaxFileSize(50 * 1024 * 1024);
        jsonUpload.addAllFinishedListener(e -> {
            byte[] bytes = jsonData.getAndSet(null);
            if (bytes == null) return;
            try {
                jsonExportImportService.importUserData(currentUser, new ByteArrayInputStream(bytes));
                Notification.show(getTranslation("settings.json_import_success"), 3000, Notification.Position.BOTTOM_END).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                com.vaadin.flow.component.UI.getCurrent().getPage().reload();
            } catch (Exception ex) {
                Notification.show(getTranslation("settings.json_import_failed", ex.getMessage()), 5000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        HorizontalLayout jsonActions = new HorizontalLayout(jsonAnchor, jsonUpload);
        jsonActions.setAlignItems(FlexComponent.Alignment.CENTER);
        jsonActions.setSpacing(false);
        jsonActions.getStyle().set("gap", "var(--lumo-space-m)").set("flex-wrap", "wrap");

        jsonCard.add(jsonActions, infoBanner(getTranslation("settings.json_warning"), true));
        container.add(jsonCard);
    }
}
