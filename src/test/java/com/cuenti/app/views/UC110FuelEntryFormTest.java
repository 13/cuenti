package com.cuenti.app.views;

import com.cuenti.app.model.Account;
import com.cuenti.app.model.Category;
import com.cuenti.app.model.Transaction;
import com.cuenti.app.model.User;
import com.cuenti.app.service.AccountService;
import com.cuenti.app.service.CategoryService;
import com.cuenti.app.service.TransactionService;
import com.cuenti.app.service.UserService;
import com.cuenti.app.usecase.UseCase;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Structured fuel entry: fuel section appears for fuel categories, fields
 * populate from an existing memo, field edits regenerate the memo string.
 */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "demo")
class UC110FuelEntryFormTest extends SpringBrowserlessTest {

    @Autowired TransactionService transactionService;
    @Autowired CategoryService categoryService;
    @Autowired AccountService accountService;
    @Autowired UserService userService;

    private Category fuelCategory;
    private Account account;

    private void seedFuelCategory() {
        User demo = userService.findByUsername("demo");
        account = accountService.getAccountsByUser(demo).get(0);
        fuelCategory = new Category();
        fuelCategory.setName("ZZ Fuel Form Test " + java.util.UUID.randomUUID());
        fuelCategory.setType(Category.CategoryType.EXPENSE);
        fuelCategory = categoryService.saveCategory(fuelCategory);
        Transaction t = new Transaction();
        t.setType(Transaction.TransactionType.EXPENSE);
        t.setFromAccount(account);
        t.setAmount(new BigDecimal("60.00"));
        t.setTransactionDate(LocalDateTime.now().minusDays(10));
        t.setCategory(fuelCategory);
        t.setMemo("d=100000 l=40 full");
        transactionService.saveTransaction(t);
    }

    @Test
    @UseCase(id = "UC-110", scenario = "Fuel section appears for fuel category")
    void fuelSection_appearsWhenFuelCategorySelected() {
        seedFuelCategory();
        navigate(TransactionHistoryView.class);
        fireShortcut(Key.KEY_N, KeyModifier.ALT);

        // $() queries only match visible components (see UC104TransactionWorkflowTest),
        // so the initially-hidden fuel-section is asserted via exists() rather than single().
        assertThat($(Div.class).withId("fuel-section").exists()).isFalse();

        ComboBox<Category> categoryCombo = $(ComboBox.class).withId("tx-category").single();
        test(categoryCombo).selectItem(fuelCategory.getFullName());

        Div fuelSection = $(Div.class).withId("fuel-section").single();
        assertThat(fuelSection.isVisible()).isTrue();
        IntegerField odometer = $(IntegerField.class).withId("fuel-odometer").single();
        assertThat(odometer.getHelperText()).contains("100000");
    }

    @Test
    @UseCase(id = "UC-110", scenario = "Field edits regenerate the memo string")
    void fieldEdits_writeCanonicalMemo() {
        seedFuelCategory();
        navigate(TransactionHistoryView.class);
        fireShortcut(Key.KEY_N, KeyModifier.ALT);

        ComboBox<Category> categoryCombo = $(ComboBox.class).withId("tx-category").single();
        test(categoryCombo).selectItem(fuelCategory.getFullName());

        test($(IntegerField.class).withId("fuel-odometer").single()).setValue(100650);
        test($(NumberField.class).withId("fuel-liters").single()).setValue(41.3);
        test($(Checkbox.class).withId("fuel-full").single()).click();

        TextArea memo = $(TextArea.class).withId("tx-memo").single();
        assertThat(memo.getValue()).isEqualTo("d=100650 l=41.3 full");
    }

    @Test
    @UseCase(id = "UC-110", scenario = "Editing a fuel transaction populates the fields")
    void editingFuelTransaction_populatesFields() {
        seedFuelCategory();
        Transaction edit = new Transaction();
        edit.setType(Transaction.TransactionType.EXPENSE);
        edit.setFromAccount(account);
        edit.setAmount(new BigDecimal("70.00"));
        edit.setTransactionDate(LocalDateTime.now().minusDays(2));
        edit.setCategory(fuelCategory);
        edit.setMemo("d=100650 l=41.3 full Aral");
        transactionService.saveTransaction(edit);

        TransactionHistoryView view = navigate(TransactionHistoryView.class);
        view.openTransactionDialog(edit);

        Div fuelSection = $(Div.class).withId("fuel-section").single();
        assertThat(fuelSection.isVisible()).isTrue();
        assertThat($(IntegerField.class).withId("fuel-odometer").single().getValue()).isEqualTo(100650);
        assertThat($(NumberField.class).withId("fuel-liters").single().getValue()).isEqualTo(41.3);
        assertThat($(Checkbox.class).withId("fuel-full").single().getValue()).isTrue();
    }

    @Test
    @UseCase(id = "UC-110", scenario = "Manual memo edit reparses into fields")
    void manualMemoEdit_reparsesIntoFields() {
        seedFuelCategory();
        navigate(TransactionHistoryView.class);
        fireShortcut(Key.KEY_N, KeyModifier.ALT);

        ComboBox<Category> categoryCombo = $(ComboBox.class).withId("tx-category").single();
        test(categoryCombo).selectItem(fuelCategory.getFullName());

        TextArea memo = $(TextArea.class).withId("tx-memo").single();
        test(memo).setValue("d=100700 l=30");

        assertThat($(IntegerField.class).withId("fuel-odometer").single().getValue()).isEqualTo(100700);
        assertThat($(NumberField.class).withId("fuel-liters").single().getValue()).isEqualTo(30.0);
        assertThat($(Checkbox.class).withId("fuel-full").single().getValue()).isFalse();
    }
}
