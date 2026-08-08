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
import com.cuenti.app.views.components.charts.ConsumptionTrendChart;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.combobox.ComboBox;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verbrauchstrend: SVG line chart with per-fill-up points and average line.
 */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "demo")
class UC109VehicleConsumptionChartTest extends SpringBrowserlessTest {

    @Autowired TransactionService transactionService;
    @Autowired CategoryService categoryService;
    @Autowired AccountService accountService;
    @Autowired UserService userService;

    @Test
    @UseCase(id = "UC-109", scenario = "Consumption trend line chart with average")
    void fuelEntries_renderTrendChartWithAverage() {
        User demo = userService.findByUsername("demo");
        Account account = accountService.getAccountsByUser(demo).get(0);

        Category fuel = new Category();
        fuel.setName("ZZ Fuel Test");
        fuel.setType(Category.CategoryType.EXPENSE);
        fuel = categoryService.saveCategory(fuel);

        LocalDate today = LocalDate.now();
        createFuelTx(fuel, account, today.atTime(8, 0),  "60.00", "v=40 d=100000");
        createFuelTx(fuel, account, today.atTime(12, 0), "60.00", "v=40 d=100500");
        createFuelTx(fuel, account, today.atTime(18, 0), "63.00", "v=42 d=101100");

        navigate(VehiclesView.class);
        ComboBox<Category> categorySelect = $(ComboBox.class).single();
        test(categorySelect).selectItem(fuel.getFullName());

        ConsumptionTrendChart chart = $(ConsumptionTrendChart.class).single();
        String svg = chart.getSvg();
        // first fill-up carries no consumption -> 2 plotted points
        assertThat(svg.split("<circle", -1).length - 1).isEqualTo(2);
        assertThat(svg).contains("<polyline");
        assertThat(svg).contains("Ø 7.45");
    }

    private void createFuelTx(Category cat, Account from, LocalDateTime date, String amount, String memo) {
        Transaction t = new Transaction();
        t.setType(Transaction.TransactionType.EXPENSE);
        t.setFromAccount(from);
        t.setAmount(new BigDecimal(amount));
        t.setCategory(cat);
        t.setMemo(memo);
        t.setPayee("Shell");
        t.setTransactionDate(date);
        t.setStatus(Transaction.TransactionStatus.COMPLETED);
        transactionService.saveTransaction(t);
    }
}
