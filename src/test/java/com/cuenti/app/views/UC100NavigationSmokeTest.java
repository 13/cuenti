package com.cuenti.app.views;

import com.cuenti.app.usecase.UseCase;
import com.vaadin.browserless.SpringBrowserlessTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test: every main view must construct and render server-side for an
 * authenticated user without throwing.
 */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "demo")
class UC100NavigationSmokeTest extends SpringBrowserlessTest {

    @Test
    @UseCase(id = "UC-100", scenario = "All primary views render")
    void allMainViewsRender() {
        assertThat(navigate(DashboardView.class)).isNotNull();
        assertThat(navigate(TransactionHistoryView.class)).isNotNull();
        assertThat(navigate(ScheduledTransactionsView.class)).isNotNull();
        assertThat(navigate(StatisticsView.class)).isNotNull();
        assertThat(navigate(ForecastsView.class)).isNotNull();
        assertThat(navigate(VehiclesView.class)).isNotNull();
        assertThat(navigate(AccountManagementView.class)).isNotNull();
        assertThat(navigate(PayeeManagementView.class)).isNotNull();
        assertThat(navigate(CategoryManagementView.class)).isNotNull();
        assertThat(navigate(TagManagementView.class)).isNotNull();
        assertThat(navigate(CurrencyManagementView.class)).isNotNull();
        assertThat(navigate(AssetManagementView.class)).isNotNull();
        assertThat(navigate(SettingsUserView.class)).isNotNull();
        assertThat(navigate(SettingsImportExportView.class)).isNotNull();
        assertThat(navigate(HelpView.class)).isNotNull();
        assertThat(navigate(AboutView.class)).isNotNull();
    }
}
