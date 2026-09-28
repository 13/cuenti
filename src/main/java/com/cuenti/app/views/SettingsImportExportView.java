package com.cuenti.app.views;

import com.cuenti.app.model.Account;
import com.cuenti.app.security.SecurityUtils;
import com.cuenti.app.service.*;
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
import com.vaadin.flow.component.upload.UploadI18N;
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
        // ── JSON Backup / Restore ─────────────────────────────────────
        Div jsonCard = createCard();
        jsonCard.add(cardHeader(VaadinIcon.ARCHIVE, getTranslation("settings.json_backup_restore"),
                getTranslation("settings.json_desc"), "var(--aura-orange)"));

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
        localize(jsonUpload);
        jsonUpload.setAcceptedFileTypes("application/json", ".json");
        jsonUpload.setUploadButton(new Button(getTranslation("settings.import_json"), VaadinIcon.UPLOAD.create()));
        jsonUpload.setMaxFiles(1);
        jsonUpload.setMaxFileSize(50 * 1024 * 1024);
        jsonUpload.addAllFinishedListener(e -> {
            byte[] bytes = jsonData.getAndSet(null);
            if (bytes == null) return;
            try {
                jsonExportImportService.importUserData(currentUser, new ByteArrayInputStream(bytes));
                com.cuenti.app.views.components.UiNotifier.success(getTranslation("settings.json_import_success"));
                com.vaadin.flow.component.UI.getCurrent().getPage().executeJs("setTimeout(() => location.reload(), 2000)");
            } catch (Exception ex) {
                com.cuenti.app.views.components.UiNotifier.error(getTranslation("settings.json_import_failed", ex.getMessage()));
            }
        });

        HorizontalLayout jsonActions = new HorizontalLayout(jsonAnchor, jsonUpload);
        jsonActions.setAlignItems(FlexComponent.Alignment.CENTER);
        jsonActions.setSpacing(false);
        jsonActions.getStyle().set("gap", "var(--vaadin-gap-m)").set("flex-wrap", "wrap");

        jsonCard.add(jsonActions, infoBanner(getTranslation("settings.json_warning"), true));
        container.add(jsonCard);

        // ── Trade Republic ────────────────────────────────────────────
        Div trCard = createCard();
        trCard.add(cardHeader(VaadinIcon.STOCK, getTranslation("settings.tr_import_title"),
                getTranslation("settings.tr_desc"), "var(--aura-green)"));

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
        trAccounts.getStyle().set("gap", "var(--vaadin-gap-m)").set("flex-wrap", "wrap");
        cashAccountCombo.getStyle().set("flex", "1 1 200px");
        assetAccountCombo.getStyle().set("flex", "1 1 200px");

        AtomicReference<byte[]> trData = new AtomicReference<>();
        Upload trUpload = new Upload(new InMemoryUploadHandler((meta, bytes) -> trData.set(bytes)));
        localize(trUpload);
        trUpload.setAcceptedFileTypes(".csv");
        trUpload.setUploadButton(new Button(getTranslation("settings.tr_import_btn"), VaadinIcon.FILE_TEXT.create()));
        // Uploading only makes sense once both (different) target accounts are chosen;
        // otherwise the file would be received and then thrown away.
        Runnable updateTrUpload = () -> {
            Account cash = cashAccountCombo.getValue();
            Account asset = assetAccountCombo.getValue();
            boolean same = cash != null && asset != null && java.util.Objects.equals(cash.getId(), asset.getId());
            assetAccountCombo.setInvalid(same);
            assetAccountCombo.setErrorMessage(same ? getTranslation("settings.tr_same_account") : null);
            boolean ready = cash != null && asset != null && !same;
            trUpload.setEnabled(ready);
            trUpload.getUploadButton().getElement().setEnabled(ready);
        };
        cashAccountCombo.addValueChangeListener(e -> updateTrUpload.run());
        assetAccountCombo.addValueChangeListener(e -> updateTrUpload.run());
        updateTrUpload.run();
        trUpload.addAllFinishedListener(e -> {
            byte[] bytes = trData.getAndSet(null);
            trUpload.clearFileList();
            if (bytes == null) return;
            if (cashAccountCombo.isEmpty() || assetAccountCombo.isEmpty()) {
                com.cuenti.app.views.components.UiNotifier.error(getTranslation("settings.tr_select_accounts"));
                return;
            }
            try {
                TradeRepublicImportService.ImportResult result = tradeRepublicImportService.importCsv(
                        new ByteArrayInputStream(bytes), cashAccountCombo.getValue(), assetAccountCombo.getValue(), currentUser);
                String message = getTranslation("settings.tr_result", result.imported(), result.skipped(), result.failed());
                if (result.failed() > 0) {
                    com.cuenti.app.views.components.UiNotifier.warning(message);
                } else {
                    com.cuenti.app.views.components.UiNotifier.success(message);
                }
            } catch (Exception ex) {
                com.cuenti.app.views.components.UiNotifier.error(getTranslation("settings.import_failed", ex.getMessage()));
            }
        });

        trCard.add(trAccounts, trUpload);
        container.add(trCard);

        // ── Homebank XHB ──────────────────────────────────────────────
        Div card = createCard();
        card.add(cardHeader(VaadinIcon.DATABASE, getTranslation("settings.import_export_title"),
                getTranslation("settings.data_desc"), "var(--aura-accent-color)"));

        AtomicReference<byte[]> xhbData = new AtomicReference<>();
        Upload upload = new Upload(new InMemoryUploadHandler((meta, bytes) -> xhbData.set(bytes)));
        localize(upload);
        upload.setAcceptedFileTypes(".xhb");
        upload.setUploadButton(new Button(getTranslation("settings.import"), VaadinIcon.UPLOAD.create()));
        upload.addAllFinishedListener(e -> {
            byte[] bytes = xhbData.getAndSet(null);
            if (bytes == null) return;
            try {
                xhbImportService.importXhb(new ByteArrayInputStream(bytes), currentUser);
                com.cuenti.app.views.components.UiNotifier.success(getTranslation("settings.import_success"));
            } catch (Exception ex) {
                com.cuenti.app.views.components.UiNotifier.error(getTranslation("settings.import_failed", ex.getMessage()));
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
        xhbActions.getStyle().set("gap", "var(--vaadin-gap-m)").set("flex-wrap", "wrap");
        card.add(xhbActions);
        container.add(card);
    }

    /** The upload widget ships English-only texts ("Drop files here", …). */
    private void localize(Upload upload) {
        UploadI18N i18n = new UploadI18N();
        i18n.setDropFiles(new UploadI18N.DropFiles()
                .setOne(getTranslation("upload.drop_file"))
                .setMany(getTranslation("upload.drop_files")));
        i18n.setError(new UploadI18N.Error()
                .setTooManyFiles(getTranslation("upload.too_many_files"))
                .setFileIsTooBig(getTranslation("upload.file_too_big"))
                .setIncorrectFileType(getTranslation("upload.incorrect_file_type")));
        i18n.setFile(new UploadI18N.File()
                .setRetry(getTranslation("upload.retry"))
                .setStart(getTranslation("upload.start"))
                .setRemove(getTranslation("upload.remove")));
        i18n.setUploading(new UploadI18N.Uploading()
                .setStatus(new UploadI18N.Uploading.Status()
                        .setConnecting(getTranslation("upload.connecting"))
                        .setStalled(getTranslation("upload.stalled"))
                        .setProcessing(getTranslation("upload.processing"))
                        .setHeld(getTranslation("upload.held")))
                .setError(new UploadI18N.Uploading.Error()
                        .setServerUnavailable(getTranslation("upload.server_unavailable"))
                        .setUnexpectedServerError(getTranslation("upload.server_error"))
                        .setForbidden(getTranslation("upload.forbidden"))
                        .setFileTooLarge(getTranslation("upload.file_too_big"))));
        upload.setI18n(i18n);
    }
}
