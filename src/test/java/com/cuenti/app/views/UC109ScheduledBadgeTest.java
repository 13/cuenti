package com.cuenti.app.views;

import com.cuenti.app.usecase.UseCase;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The scheduled-transactions nav badge must reflect the current due-soon count
 * after posting or skipping a schedule, not the count from layout creation.
 */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "demo")
class UC109ScheduledBadgeTest extends SpringBrowserlessTest {

    @Test
    @UseCase(id = "UC-109", scenario = "Posting a due schedule refreshes the nav badge")
    void postingDueSchedule_updatesNavBadge() {
        navigate(ScheduledTransactionsView.class);

        Span badge = $(Span.class).withClassName("nav-badge").single();
        long before = Long.parseLong(badge.getText());
        assertThat(before).isGreaterThan(0);

        Grid<?> pendingGrid = $(Grid.class).all().stream()
                .map(g -> (Grid<?>) g)
                .filter(g -> g.getColumns().stream()
                        .anyMatch(c -> "pending-account".equals(c.getKey())))
                .findFirst().orElseThrow();

        HorizontalLayout actions = (HorizontalLayout) test(pendingGrid)
                .getCellComponent(0, pendingGrid.getColumns().size() - 1);
        Button postButton = (Button) actions.getComponentAt(0);
        test(postButton).click();

        long after = $(Span.class).withClassName("nav-badge").exists()
                ? Long.parseLong($(Span.class).withClassName("nav-badge").single().getText())
                : 0;
        assertThat(after).isEqualTo(before - 1);
    }
}
