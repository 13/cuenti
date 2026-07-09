package com.cuenti.app.views;

import com.cuenti.app.usecase.UseCase;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.browserless.SpringBrowserlessTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tag management: dialog validation and the delete-confirmation flow
 * introduced to guard destructive actions.
 */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "demo")
class UC101ManageTagsTest extends SpringBrowserlessTest {

    private static final String TEST_TAG = "BrowserlessTestTag";

    private static Predicate<Button> icon(String vaadinIcon) {
        return b -> b.getIcon() instanceof Icon i
                && vaadinIcon.equals(i.getElement().getAttribute("icon"));
    }

    /** The grid filter field is the only TextField with a placeholder on this view. */
    private TextField searchField() {
        return $(TextField.class)
                .withCondition(f -> f.getPlaceholder() != null && !f.getPlaceholder().isEmpty())
                .single();
    }

    /** The tag dialog's name field has no placeholder (unlike the grid filter). */
    private TextField dialogNameField() {
        return $(TextField.class)
                .withCondition(f -> f.getPlaceholder() == null || f.getPlaceholder().isEmpty())
                .single();
    }

    /** Delete button lives in the actions column (last), rendered per row. */
    private Button trashButton(Grid<?> grid, int row) {
        var cell = test(grid).getCellComponent(row, grid.getColumns().size() - 1);
        return $(Button.class, cell).withCondition(icon("vaadin:trash")).single();
    }

    private void createTag(String name) {
        test($(Button.class).withCondition(icon("vaadin:plus")).single()).click();
        test(dialogNameField()).setValue(name);
        test($(Button.class).withCondition(icon("vaadin:check")).single()).click();
    }

    @Test
    @UseCase(id = "UC-101", scenario = "A1: Missing Name")
    void savingTagWithoutName_marksFieldInvalid_andKeepsDialogOpen() {
        navigate(TagManagementView.class);

        test($(Button.class).withCondition(icon("vaadin:plus")).single()).click();
        assertThat($(Dialog.class).exists()).isTrue();

        test($(Button.class).withCondition(icon("vaadin:check")).single()).click();

        assertThat(dialogNameField().isInvalid()).isTrue();
        assertThat($(Dialog.class).exists()).isTrue();
    }

    @Test
    @UseCase(id = "UC-101", scenario = "A3: Empty Grid Shows Empty State")
    void filteredOutGrid_hasEmptyStateComponent() {
        navigate(TagManagementView.class);
        Grid<?> grid = $(Grid.class).single();

        assertThat(grid.getEmptyStateComponent())
                .isInstanceOf(com.cuenti.app.views.components.EmptyStateNotice.class);

        test(searchField()).setValue("definitely-no-such-tag-xyz");
        assertThat(test(grid).size()).isEqualTo(0);
    }

    @Test
    @UseCase(id = "UC-101", scenario = "A4: Row Buttons Accessible")
    void rowActionButtons_haveAriaLabels() {
        navigate(TagManagementView.class);
        createTag(TEST_TAG + "A11y");
        test(searchField()).setValue(TEST_TAG + "A11y");
        Grid<?> grid = $(Grid.class).single();

        Button trash = trashButton(grid, 0);
        assertThat(trash.getElement().getAttribute("aria-label")).isNotBlank();

        // cleanup through the UI
        test(trash).click();
        test($(ConfirmDialog.class).single()).confirm();
    }

    @Test
    @UseCase(id = "UC-101", scenario = "A5: Undo Restores Deleted Tag")
    void undoAfterDelete_restoresTag() {
        navigate(TagManagementView.class);
        createTag(TEST_TAG + "Undo");
        test(searchField()).setValue(TEST_TAG + "Undo");
        Grid<?> grid = $(Grid.class).single();
        assertThat(test(grid).size()).isEqualTo(1);

        test(trashButton(grid, 0)).click();
        test($(ConfirmDialog.class).single()).confirm();
        assertThat(test(grid).size()).isEqualTo(0);

        // Undo action lives inside the delete notification (the earlier
        // "saved" toast may still be open, so scan all notifications)
        Button undo = $(Notification.class).all().stream()
                .flatMap(n -> $(Button.class, n).all().stream())
                .findFirst().orElseThrow();
        test(undo).click();
        assertThat(test(grid).size()).isEqualTo(1);

        // cleanup: delete again, this time for good
        test(trashButton(grid, 0)).click();
        test($(ConfirmDialog.class).single()).confirm();
        assertThat(test(grid).size()).isEqualTo(0);
    }

    @Test
    @UseCase(id = "UC-101", scenario = "A2: Delete Requires Confirmation")
    void deleteTag_showsConfirmation_cancelKeeps_confirmRemoves() {
        navigate(TagManagementView.class);
        createTag(TEST_TAG);

        test(searchField()).setValue(TEST_TAG);
        Grid<?> grid = $(Grid.class).single();
        assertThat(test(grid).size()).isEqualTo(1);

        // Cancel keeps the tag
        test(trashButton(grid, 0)).click();
        assertThat($(ConfirmDialog.class).exists()).isTrue();
        test($(ConfirmDialog.class).single()).cancel();
        assertThat(test(grid).size()).isEqualTo(1);

        // Confirm removes the tag (also cleans up the test data)
        test(trashButton(grid, 0)).click();
        test($(ConfirmDialog.class).single()).confirm();

        assertThat(test(grid).size()).isEqualTo(0);
        assertThat($(Notification.class).exists()).isTrue();
    }
}
