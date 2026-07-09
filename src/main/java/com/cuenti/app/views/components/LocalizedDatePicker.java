package com.cuenti.app.views.components;

import com.vaadin.flow.component.datepicker.DatePicker;

import java.util.List;
import java.util.Locale;

/**
 * Applies locale-specific DatePicker i18n. Currently German gets explicit
 * month/weekday names and dd.MM.yyyy format (matching the previous inline
 * configuration duplicated across views).
 */
public final class LocalizedDatePicker {

    private LocalizedDatePicker() {}

    public static void applyLocale(DatePicker picker, Locale locale) {
        picker.setLocale(locale);
        if (locale != null && "de".equals(locale.getLanguage())) {
            picker.setI18n(germanI18n());
        }
    }

    public static DatePicker.DatePickerI18n germanI18n() {
        DatePicker.DatePickerI18n i18n = new DatePicker.DatePickerI18n();
        i18n.setDateFormat("dd.MM.yyyy");
        i18n.setMonthNames(List.of("Januar", "Februar", "März", "April", "Mai", "Juni",
                "Juli", "August", "September", "Oktober", "November", "Dezember"));
        i18n.setWeekdays(List.of("Sonntag", "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag"));
        i18n.setWeekdaysShort(List.of("So", "Mo", "Di", "Mi", "Do", "Fr", "Sa"));
        i18n.setToday("Heute");
        i18n.setCancel("Abbrechen");
        i18n.setFirstDayOfWeek(1);
        return i18n;
    }
}
