package com.cuenti.app.views;

import com.cuenti.app.model.User;
import com.cuenti.app.repository.UserRepository;
import com.cuenti.app.security.TwoFactorSession;
import com.cuenti.app.usecase.UseCase;
import com.vaadin.browserless.SpringBrowserlessTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "demo1")
class UC117TwoFactorSignInTest extends SpringBrowserlessTest {

    @Autowired UserRepository userRepository;

    @BeforeEach
    void enableTwoFactor() {
        setTwoFactor(true);
    }

    @AfterEach
    void disableTwoFactor() {
        setTwoFactor(false);
    }

    private void setTwoFactor(boolean on) {
        User user = userRepository.findByUsername("demo1").orElseThrow();
        user.setTotpEnabled(on);
        user.setTotpSecret(on ? "JBSWY3DPEHPK3PXP" : null);
        userRepository.save(user);
        // UserService caches the signed-in user per Vaadin session
        com.vaadin.flow.server.VaadinSession.getCurrent().setAttribute("cuenti.session.user", null);
    }

    @Test
    @UseCase(id = "UC-117", scenario = "Password alone reaches only the code page")
    void unverifiedSessionIsSentToTheCodePage() {
        navigate("transactions", TwoFactorView.class);
        assertThat($(TwoFactorView.class).all()).hasSize(1);
        assertThat($(TransactionHistoryView.class).all()).isEmpty();
    }

    @Test
    @UseCase(id = "UC-117", scenario = "After the second factor the app opens")
    void verifiedSessionReachesTheApp() {
        TwoFactorSession.markVerified("demo1");
        assertThat(navigate(TransactionHistoryView.class)).isNotNull();
    }
}
