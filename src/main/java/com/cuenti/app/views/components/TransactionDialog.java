package com.cuenti.app.views.components;

import com.cuenti.app.model.*;
import com.cuenti.app.service.*;
import com.cuenti.app.service.VehicleReportService.FuelTokens;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Create/edit form for one transaction: type pills, amount hero, account, payee,
 * category, tags, memo, fuel entry, splits and asset details. Used by the
 * transaction list and by scheduled bookings ("Anpassen & buchen").
 */
public class TransactionDialog extends Dialog {

    /** How the dialog is opened and what happens on save. */
    public static class Options {
        Account defaultAccount;
        Runnable onSaved = () -> { };
        Function<Transaction, Transaction> saver;
        boolean allowAddAnother = true;
        boolean allowScheduleCopy = true;
        String saveLabel;

        /** Account preselected for a new transaction. */
        public Options defaultAccount(Account account) { this.defaultAccount = account; return this; }
        /** Called after every successful save (e.g. refresh a grid). */
        public Options onSaved(Runnable onSaved) { this.onSaved = onSaved; return this; }
        /** Persists the filled-in transaction; defaults to {@link TransactionService#saveTransaction}. */
        public Options saver(Function<Transaction, Transaction> saver) { this.saver = saver; return this; }
        /** Show "add & keep open" (only sensible for free entry). */
        public Options allowAddAnother(boolean allow) { this.allowAddAnother = allow; return this; }
        /** Offer "save as schedule" for existing transactions. */
        public Options allowScheduleCopy(boolean allow) { this.allowScheduleCopy = allow; return this; }
        /** Replaces the primary button's label. */
        public Options saveLabel(String label) { this.saveLabel = label; return this; }
    }

    private final TransactionService transactionService;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final AssetService assetService;
    private final PayeeService payeeService;
    private final TagService tagService;
    private final VehicleReportService vehicleReportService;
    private final User currentUser;
    private final Options options;
    private final TransactionDialogServices services;
    private Transaction.TransactionType selectedType;

    public TransactionDialog(TransactionDialogServices services, User currentUser, Transaction transaction, Options options) {
        this.transactionService = services.getTransactionService();
        this.accountService = services.getAccountService();
        this.categoryService = services.getCategoryService();
        this.assetService = services.getAssetService();
        this.payeeService = services.getPayeeService();
        this.tagService = services.getTagService();
        this.vehicleReportService = services.getVehicleReportService();
        this.currentUser = currentUser;
        this.options = options;
        this.services = services;
        if (options.saver == null) {
            options.saver = transactionService::saveTransaction;
        }
 // package-visible for tests
        final Transaction[] currentFormTransaction = {transaction};
        Dialog dialog = this;
        dialog.setCloseOnOutsideClick(false);
        dialog.setWidth("min(700px, 96vw)");
        dialog.setResizable(false);
        dialog.getElement().getStyle()
                .set("padding", "0")
                .set("overflow-x", "hidden");

        // ── Type selector: coloured pill buttons ─────────────────────
        Button[] typeBtns = TransactionFormParts.typeButtons(this);
        Div accentBar = TransactionFormParts.accentBar();
        HorizontalLayout typeRow = TransactionFormParts.typeRow(typeBtns);
        selectedType = currentFormTransaction[0].getType() != null
                ? currentFormTransaction[0].getType() : Transaction.TransactionType.EXPENSE;
        Runnable[] onTypeChanged = {() -> { }};
        for (int i = 0; i < typeBtns.length; i++) {
            Transaction.TransactionType type = TransactionFormParts.TYPES[i];
            typeBtns[i].addClickListener(e -> { selectedType = type; onTypeChanged[0].run(); });
        }

        // ── Hero: Amount field ────────────────────────────────────────
        BigDecimalField amountField = new BigDecimalField();
        amountField.setWidthFull();
        amountField.setRequiredIndicatorVisible(true);
        amountField.setValue(currentFormTransaction[0].getAmount() != null ? currentFormTransaction[0].getAmount() : BigDecimal.ZERO);
        TransactionFormParts.styleHeroAmount(amountField);

        // Split toggle button — next to amount
        Button splitToggleBtn = new Button(VaadinIcon.PIE_CHART.create());
        splitToggleBtn.setTooltipText(getTranslation("transactions.split_transaction"));
        splitToggleBtn.getElement().setAttribute("aria-label", getTranslation("transactions.split_transaction"));splitToggleBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        splitToggleBtn.getStyle().set("flex-shrink", "0");

        HorizontalLayout amountRow = new HorizontalLayout(amountField, splitToggleBtn);
        amountRow.setWidthFull();
        amountRow.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);
        amountRow.setSpacing(false);
        amountRow.getStyle().set("gap", "var(--vaadin-gap-xs)");
        amountRow.expand(amountField);

        Div heroSection = TransactionFormParts.heroSection(this, amountRow);

        // ── Date picker ───────────────────────────────────────────────
        DatePicker datePicker = new DatePicker(getTranslation("dialog.date"));
        LocalizedDatePicker.applyLocale(datePicker, currentUser.getLocale() != null
                ? Locale.forLanguageTag(currentUser.getLocale()) : getLocale());
        datePicker.setValue(currentFormTransaction[0].getTransactionDate() != null
                ? currentFormTransaction[0].getTransactionDate().toLocalDate() : LocalDateTime.now().toLocalDate());
        datePicker.setWidthFull();

        // ── Account fields ────────────────────────────────────────────
        List<Account> userAccounts = accountService.getAccountsByUser(currentUser);
        ComboBox<Account> accountCombo = new ComboBox<>(getTranslation("dialog.account"));
        accountCombo.setItems(userAccounts);
        accountCombo.setItemLabelGenerator(Account::getAccountName);
        accountCombo.setRequired(true);
        accountCombo.setWidthFull();
        if (currentFormTransaction[0].getId() == null && options.defaultAccount != null) {
            accountCombo.setValue(options.defaultAccount);
        }

        ComboBox<Account> toAccountCombo = new ComboBox<>(getTranslation("dialog.to"));
        toAccountCombo.setItems(userAccounts);
        toAccountCombo.setItemLabelGenerator(Account::getAccountName);
        toAccountCombo.setWidthFull();

        // ── Payee + Category ──────────────────────────────────────────
        ComboBox<String> payeeCombo = new ComboBox<>(getTranslation("transactions.payee"));
        List<Payee> allPayeesForAutofill = payeeService.getAllPayees();
        List<String> existingPayees = allPayeesForAutofill.stream().map(Payee::getName).distinct().toList();
        payeeCombo.setItems(existingPayees);
        payeeCombo.setAllowCustomValue(true);
        payeeCombo.setValue(currentFormTransaction[0].getPayee());
        payeeCombo.addCustomValueSetListener(e -> payeeCombo.setValue(e.getDetail()));
        payeeCombo.setWidthFull();
        payeeCombo.setPrefixComponent(VaadinIcon.USER.create());

        ComboBox<Category> categoryCombo = new ComboBox<>(getTranslation("transactions.category"));
        categoryCombo.setId("tx-category");
        categoryCombo.setItemLabelGenerator(Category::getFullName);
        categoryCombo.setAllowCustomValue(true);
        categoryCombo.setWidthFull();
        categoryCombo.addCustomValueSetListener(e -> {
            Category.CategoryType categoryType = selectedType == Transaction.TransactionType.INCOME
                    ? Category.CategoryType.INCOME : Category.CategoryType.EXPENSE;
            Category saved = TransactionFormParts.createCategory(this, categoryService, currentUser, e.getDetail(), categoryType);
            categoryCombo.setItems(filteredCategories());
            categoryCombo.setValue(saved);
        });

        // ── Payment + Number (secondary) ─────────────────────────────
        ComboBox<Transaction.PaymentMethod> paymentCombo = new ComboBox<>(getTranslation("dialog.payment_method"));
        paymentCombo.setItems(Transaction.PaymentMethod.values());
        paymentCombo.setItemLabelGenerator(pm -> pm == Transaction.PaymentMethod.NONE ? getTranslation("dialog.none") : getTranslation("payment_method." + pm.name()));
        paymentCombo.setValue(currentFormTransaction[0].getPaymentMethod() != null
                ? currentFormTransaction[0].getPaymentMethod() : Transaction.PaymentMethod.NONE);
        paymentCombo.setWidthFull();

        TextField numberField = new TextField(getTranslation("dialog.number"));
        numberField.setValue(currentFormTransaction[0].getNumber() != null ? currentFormTransaction[0].getNumber() : "");
        numberField.setWidthFull();

        // ── Tags + Memo ───────────────────────────────────────────────
        TagField tagsField = new TagField(tagService, getTranslation("dialog.tags"));
        tagsField.setId("tx-tags");
        tagsField.setValue(currentFormTransaction[0].getTags());
        tagsField.setSuggestionsFor(currentFormTransaction[0].getPayee());
        payeeCombo.addValueChangeListener(e -> tagsField.setSuggestionsFor(e.getValue()));

        TextArea memoField = new TextArea(getTranslation("dialog.memo"));
        memoField.setId("tx-memo");
        memoField.setValue(currentFormTransaction[0].getMemo() != null ? currentFormTransaction[0].getMemo() : "");
        memoField.setWidthFull();
        memoField.setMinHeight("60px");
        memoField.setMaxHeight("100px");

        // ── Fuel section (structured tanking entry) ───────────────────
        IntegerField fuelOdometerField = new IntegerField(getTranslation("vehicles.form_odometer"));
        fuelOdometerField.setId("fuel-odometer");
        fuelOdometerField.setWidthFull();
        fuelOdometerField.setStepButtonsVisible(false);

        NumberField fuelLitersField = new NumberField(getTranslation("vehicles.form_liters"));
        fuelLitersField.setId("fuel-liters");
        fuelLitersField.setWidthFull();

        Checkbox fuelFullTankBox = new Checkbox(getTranslation("vehicles.form_full_tank"));
        fuelFullTankBox.setId("fuel-full");

        HorizontalLayout fuelRow = new HorizontalLayout(fuelOdometerField, fuelLitersField, fuelFullTankBox);
        fuelRow.setWidthFull(); fuelRow.setSpacing(false);
        fuelRow.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.BASELINE);
        fuelRow.getStyle().set("gap", "var(--vaadin-gap-m)").set("flex-wrap", "wrap");
        fuelOdometerField.getElement().getStyle().set("flex", "1 1 140px").set("min-width", "0");
        fuelLitersField.getElement().getStyle().set("flex", "1 1 140px").set("min-width", "0");

        Div fuelSection = new Div(fuelRow);
        fuelSection.setId("fuel-section");
        fuelSection.setWidthFull();
        fuelSection.setVisible(false);

        String[] fuelRemainder = {""};
        boolean[] fuelSyncing = {false};
        BigDecimal[] fuelLastOdometer = {null};

        Runnable[] updateFuelHintsRef = new Runnable[1];
        Runnable refreshLastOdometerHint = () -> {
            Category cat = categoryCombo.getValue();
            LocalDate refDate = datePicker.getValue() != null ? datePicker.getValue() : LocalDate.now();
            fuelLastOdometer[0] = cat != null && cat.getId() != null
                    ? vehicleReportService.lastOdometer(currentUser, cat.getId(), refDate)
                    : null;
            fuelOdometerField.setHelperText(fuelLastOdometer[0] != null
                    ? getTranslation("vehicles.form_last", fuelLastOdometer[0].toPlainString())
                    : null);
            if (updateFuelHintsRef[0] != null) updateFuelHintsRef[0].run();
        };

        Runnable syncMemoFromFuelFields = () -> {
            if (fuelSyncing[0]) return;
            fuelSyncing[0] = true;
            BigDecimal od = fuelOdometerField.getValue() != null
                    ? BigDecimal.valueOf(fuelOdometerField.getValue()) : null;
            BigDecimal li = fuelLitersField.getValue() != null
                    ? BigDecimal.valueOf(fuelLitersField.getValue()) : null;
            memoField.setValue(VehicleReportService.buildFuelMemo(
                    od, li, Boolean.TRUE.equals(fuelFullTankBox.getValue()), fuelRemainder[0]));
            fuelSyncing[0] = false;
        };

        Span fuelInfoLine = new Span();
        fuelInfoLine.setId("fuel-info");
        fuelInfoLine.setVisible(false);
        fuelSection.add(fuelInfoLine);

        Runnable updateFuelHints = () -> {
            // liters plausibility
            Double liters = fuelLitersField.getValue();
            fuelLitersField.setHelperText(liters != null && (liters <= 0 || liters > 200)
                    ? getTranslation("vehicles.warn_liters_implausible") : null);

            // odometer vs last known
            Integer odometer = fuelOdometerField.getValue();
            BigDecimal last = fuelLastOdometer[0];
            fuelInfoLine.setVisible(false);
            fuelInfoLine.getElement().getThemeList().clear();
            if (odometer == null || last == null) return;

            BigDecimal distance = BigDecimal.valueOf(odometer).subtract(last);
            if (distance.compareTo(BigDecimal.ZERO) <= 0) {
                fuelInfoLine.setText(getTranslation("vehicles.warn_odometer_not_increasing", last.toPlainString()));
                fuelInfoLine.getElement().getThemeList().addAll(java.util.List.of("badge", "warning"));
                fuelInfoLine.setVisible(true);
            } else if (distance.compareTo(BigDecimal.valueOf(2000)) > 0) {
                fuelInfoLine.setText(getTranslation("vehicles.warn_odometer_jump", distance.toPlainString()));
                fuelInfoLine.getElement().getThemeList().addAll(java.util.List.of("badge", "warning"));
                fuelInfoLine.setVisible(true);
            } else if (liters != null && liters > 0 && Boolean.TRUE.equals(fuelFullTankBox.getValue())) {
                BigDecimal consumption = BigDecimal.valueOf(liters)
                        .divide(distance, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
                fuelInfoLine.setText(getTranslation("vehicles.form_info_consumption",
                        distance.toPlainString(), consumption.toPlainString()));
                fuelInfoLine.getElement().getThemeList().add("badge");
                fuelInfoLine.setVisible(true);
            } else {
                fuelInfoLine.setText(getTranslation("vehicles.form_info", distance.toPlainString()));
                fuelInfoLine.getElement().getThemeList().add("badge");
                fuelInfoLine.setVisible(true);
            }
        };
        updateFuelHintsRef[0] = updateFuelHints;

        fuelOdometerField.addValueChangeListener(e -> { syncMemoFromFuelFields.run(); updateFuelHints.run(); });
        fuelLitersField.addValueChangeListener(e -> { syncMemoFromFuelFields.run(); updateFuelHints.run(); });
        fuelFullTankBox.addValueChangeListener(e -> { syncMemoFromFuelFields.run(); updateFuelHints.run(); });

        Runnable populateFuelFieldsFromMemo = () -> {
            FuelTokens tokens = VehicleReportService.parseFuelTokens(memoField.getValue());
            fuelSyncing[0] = true;
            fuelOdometerField.setValue(tokens.odometer() != null ? tokens.odometer().intValue() : null);
            fuelLitersField.setValue(tokens.liters() != null ? tokens.liters().doubleValue() : null);
            fuelFullTankBox.setValue(tokens.fullTank());
            fuelRemainder[0] = tokens.remainderText();
            fuelSyncing[0] = false;
        };
        memoField.addValueChangeListener(e -> {
            if (fuelSyncing[0] || !e.isFromClient()) return;
            populateFuelFieldsFromMemo.run();
            updateFuelHints.run();
        });

        Runnable updateFuelVisibility = () -> {
            Category cat = categoryCombo.getValue();
            boolean memoParses = VehicleReportService.parseFuelTokens(memoField.getValue()).hasFuelData();
            boolean show = memoParses || (cat != null && cat.getId() != null
                    && vehicleReportService.isFuelCategory(currentUser, cat.getId()));
            fuelSection.setVisible(show);
            if (show) refreshLastOdometerHint.run();
        };
        categoryCombo.addValueChangeListener(e -> updateFuelVisibility.run());
        datePicker.addValueChangeListener(e -> { if (fuelSection.isVisible()) refreshLastOdometerHint.run(); });

        // Autofill fields from payee defaults when a payee is selected
        payeeCombo.addValueChangeListener(e -> {
            if (!e.isFromClient()) return;
            String selectedName = e.getValue();
            if (selectedName == null || selectedName.isEmpty()) return;
            allPayeesForAutofill.stream()
                    .filter(p -> p.getName().equalsIgnoreCase(selectedName))
                    .findFirst()
                    .ifPresent(payee -> {
                        if (payee.getDefaultCategory() != null) {
                            categoryCombo.setItems(categoryService.getCategoriesByType(payee.getDefaultCategory().getType()));
                            categoryCombo.setValue(payee.getDefaultCategory());
                        }
                        if (payee.getDefaultPaymentMethod() != null
                                && payee.getDefaultPaymentMethod() != Transaction.PaymentMethod.NONE) {
                            paymentCombo.setValue(payee.getDefaultPaymentMethod());
                        }
                        if (payee.getDefaultMemo() != null && !payee.getDefaultMemo().isEmpty()) {
                            memoField.setValue(payee.getDefaultMemo());
                        }
                        // defaults are added to what the user already picked, never replace it
                        tagsField.addTags(com.cuenti.app.util.TagNames.parse(payee.getDefaultTags()));
                    });
        });

        // ── Asset section (conditional) ───────────────────────────────
        ComboBox<Asset> assetCombo = new ComboBox<>(getTranslation("dialog.asset"));
        assetCombo.setItems(assetService.getAllAssets());
        assetCombo.setItemLabelGenerator(Asset::getSymbol);
        assetCombo.setValue(currentFormTransaction[0].getAsset());
        assetCombo.setWidthFull();

        BigDecimalField unitsField = new BigDecimalField(getTranslation("dialog.units"));
        unitsField.setValue(currentFormTransaction[0].getUnits() != null ? currentFormTransaction[0].getUnits() : BigDecimal.ZERO);
        unitsField.setWidthFull();

        BigDecimalField unitPriceField = new BigDecimalField(getTranslation("dialog.unit_price"));
        unitPriceField.setWidthFull();

        Div assetSection = TransactionFormParts.formSection(getTranslation("dialog.asset_details"));
        HorizontalLayout assetRow = new HorizontalLayout(assetCombo, unitsField, unitPriceField);
        assetRow.setWidthFull(); assetRow.setSpacing(false);
        assetRow.getStyle().set("gap", "var(--vaadin-gap-s)").set("flex-wrap", "wrap");
        assetCombo.getStyle().set("flex", "1 1 160px"); unitsField.getStyle().set("flex", "1 1 100px"); unitPriceField.getStyle().set("flex", "1 1 100px");
        assetSection.add(assetRow);

        // ── Splits section ────────────────────────────────────────────
        List<TransactionSplit> currentSplits = new ArrayList<>(
                currentFormTransaction[0].getSplits() != null ? currentFormTransaction[0].getSplits() : new ArrayList<>());

        com.vaadin.flow.component.grid.Grid<TransactionSplit> splitGrid =
                new com.vaadin.flow.component.grid.Grid<>(TransactionSplit.class, false);
        splitGrid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_COMPACT);
        splitGrid.addColumn(TransactionSplit::getAmount)
                .setHeader(getTranslation("dialog.amount")).setAutoWidth(true);
        splitGrid.addColumn(s -> s.getCategory() != null ? s.getCategory().getFullName() : "")
                .setHeader(getTranslation("transactions.category")).setAutoWidth(true);
        splitGrid.addColumn(TransactionSplit::getMemo)
                .setHeader(getTranslation("dialog.memo")).setAutoWidth(true);

        BigDecimalField splitAmountField    = new BigDecimalField(getTranslation("dialog.amount"));
        ComboBox<Category> splitCategoryCombo = new ComboBox<>(getTranslation("transactions.category"));
        splitCategoryCombo.setItemLabelGenerator(Category::getFullName);
        splitCategoryCombo.setItems(filteredCategories());
        TextField splitMemoField = new TextField(getTranslation("dialog.memo"));

        splitGrid.addComponentColumn(s -> {
            Button editBtn = new Button(VaadinIcon.EDIT.create(), ev -> {
                currentSplits.remove(s); splitGrid.setItems(currentSplits);
                splitAmountField.setValue(s.getAmount());
                splitCategoryCombo.setValue(s.getCategory());
                splitMemoField.setValue(s.getMemo() != null ? s.getMemo() : "");
                updateTotalAmount(currentSplits, amountField, categoryCombo);
                if (currentSplits.isEmpty()) splitGrid.setVisible(false);
            });
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            Button deleteBtn = new Button(VaadinIcon.TRASH.create(), ev -> {
                currentSplits.remove(s); splitGrid.setItems(currentSplits);
                updateTotalAmount(currentSplits, amountField, categoryCombo);
                if (currentSplits.isEmpty()) splitGrid.setVisible(false);
            });
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            HorizontalLayout hl = new HorizontalLayout(editBtn, deleteBtn);
            hl.setSpacing(false); return hl;
        }).setAutoWidth(true);
        splitGrid.setItems(currentSplits);
        splitGrid.setAllRowsVisible(true);
        splitGrid.setVisible(!currentSplits.isEmpty());

        splitAmountField.getStyle().set("flex", "1 1 90px").set("min-width", "0");
        splitCategoryCombo.getStyle().set("flex", "2 1 130px").set("min-width", "0");
        splitMemoField.getStyle().set("flex", "1 1 90px").set("min-width", "0");
        HorizontalLayout splitInputRow = new HorizontalLayout(splitAmountField, splitCategoryCombo, splitMemoField);
        splitInputRow.setWidthFull(); splitInputRow.setSpacing(false);
        splitInputRow.getStyle().set("gap", "var(--vaadin-gap-s)").set("flex-wrap", "wrap");
        splitInputRow.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.BASELINE);

        Button addSplitBtn = new Button(getTranslation("dialog.add"), VaadinIcon.PLUS.create(), ev -> {
            if (splitAmountField.getValue() != null && splitCategoryCombo.getValue() != null) {
                currentSplits.add(TransactionSplit.builder()
                        .amount(splitAmountField.getValue())
                        .category(splitCategoryCombo.getValue())
                        .memo(splitMemoField.getValue()).build());
                splitGrid.setItems(currentSplits); splitGrid.setVisible(true);
                updateTotalAmount(currentSplits, amountField, categoryCombo);
                splitAmountField.clear(); splitCategoryCombo.clear(); splitMemoField.clear();
            }
        });
        addSplitBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        HorizontalLayout splitAddRow = new HorizontalLayout(splitInputRow, addSplitBtn);
        splitAddRow.setWidthFull(); splitAddRow.setSpacing(false);
        splitAddRow.getStyle().set("gap", "var(--vaadin-gap-s)");
        splitAddRow.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.BASELINE);
        splitAddRow.expand(splitInputRow);

        Div splitSection = TransactionFormParts.formSection(getTranslation("transactions.split_transaction"));
        splitSection.add(splitGrid, splitAddRow);
        splitSection.setVisible(!currentSplits.isEmpty());

        splitToggleBtn.addClickListener(e -> splitSection.setVisible(!splitSection.isVisible()));

        // ── Visibility logic ──────────────────────────────────────────
        Runnable updateVisibility = () -> {
            boolean isTransfer = selectedType == Transaction.TransactionType.TRANSFER;
            toAccountCombo.setVisible(isTransfer);
            paymentCombo.setVisible(!isTransfer);
            accountCombo.setLabel(isTransfer ? getTranslation("dialog.from") : getTranslation("dialog.account"));
            Category currentCat = categoryCombo.getValue();
            categoryCombo.setItems(filteredCategories());
            splitCategoryCombo.setItems(filteredCategories());
            if (currentCat != null) categoryCombo.setValue(currentCat);
            Account acc   = accountCombo.getValue();
            Account toAcc = toAccountCombo.getValue();
            boolean assetVisible = (acc != null && acc.getAccountType() == Account.AccountType.ASSET)
                    || (isTransfer && toAcc != null && toAcc.getAccountType() == Account.AccountType.ASSET);
            assetSection.setVisible(assetVisible);
            TransactionFormParts.applyType(typeBtns, accentBar, selectedType);
        };

        onTypeChanged[0] = updateVisibility;
        accountCombo.addValueChangeListener(e -> updateVisibility.run());
        toAccountCombo.addValueChangeListener(e -> updateVisibility.run());

        // Pre-fill edit mode (and prefilled new bookings, e.g. from a schedule)
        if (currentFormTransaction[0].getId() != null
                || currentFormTransaction[0].getFromAccount() != null || currentFormTransaction[0].getToAccount() != null) {
            categoryCombo.setItems(filteredCategories());
            if (currentFormTransaction[0].getType() == Transaction.TransactionType.INCOME) {
                accountCombo.setValue(currentFormTransaction[0].getToAccount());
            } else {
                accountCombo.setValue(currentFormTransaction[0].getFromAccount());
                if (currentFormTransaction[0].getType() == Transaction.TransactionType.TRANSFER)
                    toAccountCombo.setValue(currentFormTransaction[0].getToAccount());
            }
            categoryCombo.setValue(currentFormTransaction[0].getCategory());
            if (currentFormTransaction[0].getUnits() != null
                    && currentFormTransaction[0].getUnits().compareTo(BigDecimal.ZERO) != 0) {
                unitPriceField.setValue(currentFormTransaction[0].getAmount()
                        .divide(currentFormTransaction[0].getUnits(), 4, RoundingMode.HALF_UP));
            }
            if (VehicleReportService.parseFuelTokens(currentFormTransaction[0].getMemo()).hasFuelData()) {
                populateFuelFieldsFromMemo.run();
                updateFuelVisibility.run();
            }
        }
        updateVisibility.run();
        updateTotalAmount(currentSplits, amountField, categoryCombo);

        // ── Assemble sections ─────────────────────────────────────────
        // Section 1: Core — date, account, payee, category
        Div coreSection = TransactionFormParts.formSection(null);
        // wraps to single column on mobile
        HorizontalLayout row1 = new HorizontalLayout(datePicker, accountCombo);
        row1.setWidthFull(); row1.setSpacing(false);
        row1.getStyle().set("gap", "var(--vaadin-gap-m)").set("flex-wrap", "wrap");
        row1.getChildren().forEach(c -> c.getElement().getStyle().set("flex", "1 1 200px").set("min-width", "0"));
        // wraps to single column on mobile
        HorizontalLayout row2 = new HorizontalLayout(payeeCombo, categoryCombo);
        row2.setWidthFull(); row2.setSpacing(false);
        row2.getStyle().set("gap", "var(--vaadin-gap-m)").set("flex-wrap", "wrap");
        row2.getChildren().forEach(c -> c.getElement().getStyle().set("flex", "1 1 200px").set("min-width", "0"));
        // wraps to single column on mobile
        HorizontalLayout row3 = new HorizontalLayout(toAccountCombo, paymentCombo);
        row3.setWidthFull(); row3.setSpacing(false);
        row3.getStyle().set("gap", "var(--vaadin-gap-m)").set("flex-wrap", "wrap");
         row3.getChildren().forEach(c -> c.getElement().getStyle().set("flex", "1 1 200px").set("min-width", "0"));
         coreSection.add(row1, row2, fuelSection, row3, tagsField, memoField, splitSection, assetSection);

        // Secondary: payment details (number)
        Div extraSection = TransactionFormParts.formSection(null);
        extraSection.add(numberField);
        extraSection.setVisible(false);

        Button moreBtn = new Button(getTranslation("dialog.more_details"), VaadinIcon.ANGLE_DOWN.create());
        moreBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        moreBtn.getStyle().set("font-size", "var(--aura-font-size-xs)").set("color", "var(--vaadin-text-color-secondary)");
        moreBtn.addClickListener(e -> {
            boolean v = !extraSection.isVisible();
            extraSection.setVisible(v);
            moreBtn.setIcon(v ? VaadinIcon.ANGLE_UP.create() : VaadinIcon.ANGLE_DOWN.create());
        });

        // ── Full body ─────────────────────────────────────────────────
        Div body = new Div(accentBar, typeRow, heroSection, coreSection, moreBtn, extraSection);
        body.setWidthFull();
        body.getStyle()
                .set("display", "flex").set("flex-direction", "column")
                .set("overflow-x", "hidden").set("box-sizing", "border-box");

        // ── Footer buttons ────────────────────────────────────────────
        Button saveButton = new Button(
                transaction.getId() == null ? getTranslation("dialog.add") : getTranslation("dialog.save"),
                transaction.getId() == null ? VaadinIcon.CHECK.create() : VaadinIcon.CHECK.create(),
                e -> {
                    if (accountCombo.isEmpty()) {
                        com.cuenti.app.views.components.UiNotifier.error(getTranslation("accounts.name_required"));
                        return;
                    }
                    if (amountField.getValue() == null || amountField.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                        com.cuenti.app.views.components.UiNotifier.error(getTranslation("dialog.amount_positive"));
                        return;
                    }
                    Transaction saveTx = currentFormTransaction[0];
                    saveTx.getSplits().clear();
                    for (TransactionSplit s : currentSplits) saveTx.addSplit(s);
                    if (fuelSection.isVisible()
                            && fuelOdometerField.getValue() == null && fuelLitersField.getValue() == null) {
                        Notification.show(getTranslation("vehicles.warn_no_fuel_data"), 4000, Notification.Position.MIDDLE);
                    }
                    saveForm(saveTx, datePicker, amountField, accountCombo, toAccountCombo, paymentCombo, numberField, payeeCombo, categoryCombo, assetCombo, unitsField, memoField, tagsField);
                    options.onSaved.run(); dialog.close();
                    com.cuenti.app.views.components.UiNotifier.success(getTranslation("transactions.saved"));
                });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        String addKeepLabel = transaction.getId() == null ? getTranslation("dialog.add_keep") : getTranslation("dialog.save_keep");
        Button addKeepButton = new Button(addKeepLabel, e -> {
            if (accountCombo.isEmpty()) {
                com.cuenti.app.views.components.UiNotifier.error(getTranslation("accounts.name_required"));
                return;
            }
            if (amountField.getValue() == null || amountField.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                com.cuenti.app.views.components.UiNotifier.error(getTranslation("dialog.amount_positive"));
                return;
            }
            Transaction keepTx = currentFormTransaction[0];
            keepTx.getSplits().clear();
            for (TransactionSplit s : currentSplits) keepTx.addSplit(s);
            if (fuelSection.isVisible()
                    && fuelOdometerField.getValue() == null && fuelLitersField.getValue() == null) {
                Notification.show(getTranslation("vehicles.warn_no_fuel_data"), 4000, Notification.Position.MIDDLE);
            }
            saveForm(keepTx, datePicker, amountField, accountCombo, toAccountCombo, paymentCombo, numberField, payeeCombo, categoryCombo, assetCombo, unitsField, memoField, tagsField);
            options.onSaved.run();
            com.cuenti.app.views.components.UiNotifier.success(getTranslation("transactions.saved"));
            currentFormTransaction[0] = new Transaction();
            currentSplits.clear();
            updateTotalAmount(currentSplits, amountField, categoryCombo);
        });
        addKeepButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button cancelButton = new Button(getTranslation("dialog.cancel"), e -> dialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        addKeepButton.setVisible(options.allowAddAnother);
        if (options.saveLabel != null) {
            saveButton.setText(options.saveLabel);
        }

        dialog.add(body);
        if (transaction.getId() != null && options.allowScheduleCopy) {
            // Turn a recurring payment into a schedule without retyping it.
            Button scheduleButton = new Button(getTranslation("transactions.save_as_schedule"), VaadinIcon.CALENDAR_CLOCK.create(),
                    e -> new ScheduledTransactionDialog(services, currentUser,
                            ScheduledTransactionService.draftFrom(transaction),
                            () -> UiNotifier.success(getTranslation("transactions.schedule_created"))).open());
            scheduleButton.setId("tx-save-as-schedule");
            scheduleButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            scheduleButton.getStyle().set("margin-inline-end", "auto");
            dialog.getFooter().add(scheduleButton);
        }
        dialog.getFooter().add(cancelButton, addKeepButton, saveButton);
        addOpenedChangeListener(e -> { if (e.isOpened()) amountField.focus(); });
    }

    /** Categories matching the selected type; transfers may use either kind. */
    private List<Category> filteredCategories() {
        if (selectedType == Transaction.TransactionType.EXPENSE) return categoryService.getCategoriesByType(Category.CategoryType.EXPENSE);
        else if (selectedType == Transaction.TransactionType.INCOME) return categoryService.getCategoriesByType(Category.CategoryType.INCOME);
        return Stream.concat(
                        categoryService.getCategoriesByType(Category.CategoryType.EXPENSE).stream(),
                        categoryService.getCategoriesByType(Category.CategoryType.INCOME).stream())
                .distinct()
                .sorted(Comparator.comparing(Category::getFullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private void updateTotalAmount(List<TransactionSplit> splits, BigDecimalField amountField, ComboBox<Category> categoryCombo) {
        if (!splits.isEmpty()) {
            BigDecimal total = splits.stream().map(TransactionSplit::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            amountField.setValue(total);
            amountField.setReadOnly(true);
            categoryCombo.setReadOnly(true);
            // Hide the component if splits exist, but keep value for fallback if removed. Wait, it's just locked.
        } else {
            amountField.setReadOnly(false);
            categoryCombo.setReadOnly(false);
        }
    }

    private void saveForm(Transaction transaction, DatePicker datePicker, BigDecimalField amountField, ComboBox<Account> accountCombo, ComboBox<Account> toAccountCombo, ComboBox<Transaction.PaymentMethod> paymentCombo, TextField numberField, ComboBox<String> payeeCombo, ComboBox<Category> categoryCombo, ComboBox<Asset> assetCombo, BigDecimalField unitsField, TextArea memoField, TagField tagsField) {
        Transaction.TransactionType type = selectedType;
        
        transaction.setType(type);
        transaction.setTransactionDate(datePicker.getValue().atStartOfDay());
        transaction.setAmount(amountField.getValue());
        
        if (type == Transaction.TransactionType.INCOME) {
            transaction.setToAccount(accountCombo.getValue());
            transaction.setFromAccount(null);
        } else if (type == Transaction.TransactionType.EXPENSE) {
            transaction.setFromAccount(accountCombo.getValue());
            transaction.setToAccount(null);
        } else {
            transaction.setFromAccount(accountCombo.getValue());
            transaction.setToAccount(toAccountCombo.getValue());
        }

        String payeeName = payeeCombo.getValue();
        if (payeeName != null && !payeeName.isBlank()) {
            if (!payeeService.existsByName(payeeName)) {
                Payee newPayee = Payee.builder().name(payeeName).build();
                payeeService.savePayee(newPayee);
            }
        }

        transaction.setPaymentMethod(paymentCombo.getValue());
        transaction.setNumber(numberField.getValue());
        transaction.setPayee(payeeName);
        transaction.setCategory(categoryCombo.getValue());
        transaction.setAsset(assetCombo.getValue());
        transaction.setUnits(unitsField.getValue());
        transaction.setMemo(memoField.getValue());
        
        transaction.setTags(tagsField.getValue());

        if (transaction.getId() == null) {
            Account relevantAccount = type == Transaction.TransactionType.INCOME
                    ? transaction.getToAccount() : transaction.getFromAccount();
            transaction.setSortOrder(transactionService.nextSortOrder(
                    relevantAccount, transaction.getTransactionDate().toLocalDate()));
        }

        options.saver.apply(transaction);
    }
}
