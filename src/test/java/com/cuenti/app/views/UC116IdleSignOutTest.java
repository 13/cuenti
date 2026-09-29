package com.cuenti.app.views;

import com.cuenti.app.usecase.UseCase;
import com.cuenti.app.views.components.IdleLogoutGuard;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class UC116IdleSignOutTest extends SpringBrowserlessTest {

    @Test
    @WithMockUser(username = "demo")
    @UseCase(id = "UC-116", scenario = "Signed-in layout installs the idle guard")
    void mainLayoutHasIdleGuard() {
        navigate(DashboardView.class);
        assertThat($(IdleLogoutGuard.class).all()).hasSize(1);
    }

    @Test
    @UseCase(id = "UC-116", scenario = "Login page explains an idle sign-out")
    void loginShowsIdleNotice() {
        assertThat(idleNotice(navigate("login", LoginView.class)).isVisible()).isFalse();
        UI.getCurrent().navigate("login", QueryParameters.of("timeout", ""));
        assertThat(idleNotice($(LoginView.class).single()).isVisible()).isTrue();
    }

    private static Span idleNotice(Component root) {
        return findAll(root)
                .filter(c -> c instanceof Span s && s.hasClassName("auth-notice"))
                .map(Span.class::cast)
                .findFirst().orElseThrow();
    }

    private static Stream<Component> findAll(Component c) {
        return Stream.concat(Stream.of(c), c.getChildren().flatMap(UC116IdleSignOutTest::findAll));
    }
}
