package com.cuenti.app.views.components;

import com.cuenti.app.usecase.UseCase;
import com.cuenti.app.views.DashboardView;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.dialog.Dialog;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "demo")
class IdleLogoutGuardTest extends SpringBrowserlessTest {

    @Test
    @UseCase(id = "UC-116", scenario = "Warning dialog opens and activity closes it")
    void warningOpensAndActivityClosesIt() {
        navigate(DashboardView.class);
        IdleLogoutGuard guard = $(IdleLogoutGuard.class).single();

        guard.warn();
        guard.warn(); // repeated warnings from several checks open only one dialog
        assertThat($(Dialog.class).all()).filteredOn(Dialog::isOpened).hasSize(1);

        guard.active();
        assertThat($(Dialog.class).all()).noneMatch(Dialog::isOpened);
    }
}
