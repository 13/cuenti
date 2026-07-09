package com.cuenti.app.views.components;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;

/**
 * Confirmation dialog for destructive actions. Runs {@code onConfirm} only
 * after the user explicitly confirms; service exceptions in the callback are
 * caught and surfaced as an error notification instead of an internal error.
 */
public final class DeleteConfirm {

    private DeleteConfirm() {}

    /**
     * @param header       dialog title (already translated)
     * @param message      dialog body (already translated)
     * @param confirmLabel label of the destructive button (already translated)
     * @param cancelLabel  label of the cancel button (already translated)
     * @param errorMessage notification shown if onConfirm throws (already translated)
     * @param onConfirm    the delete action
     */
    public static void show(String header, String message, String confirmLabel,
                            String cancelLabel, String errorMessage, Runnable onConfirm) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader(header);
        dialog.setText(message);

        dialog.setCancelable(true);
        dialog.setCancelText(cancelLabel);

        dialog.setConfirmText(confirmLabel);
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> {
            try {
                onConfirm.run();
            } catch (Exception ex) {
                UiNotifier.error(errorMessage);
            }
        });

        UI.getCurrent().add(dialog);
        dialog.open();
    }
}
