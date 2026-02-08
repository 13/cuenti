package com.cuenti.homebanking.views;

import com.vaadin.flow.component.accordion.Accordion;
import com.vaadin.flow.component.accordion.AccordionPanel;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "help", layout = MainLayout.class)
@PageTitle("Help")
@PermitAll
public class HelpView extends VerticalLayout {

    public HelpView() {
        setSpacing(true);
        setPadding(true);
        setMaxWidth("1200px");
        getStyle().set("margin", "0 auto");

        H2 title = new H2(getTranslation("help.title", "Help"));
        add(title);

        Accordion accordion = new Accordion();
        accordion.setWidthFull();

        // Dashboard
        accordion.add(createHelpPanel(
            getTranslation("nav.dashboard", "Dashboard"),
            getTranslation("help.dashboard", "The Dashboard provides an overview of your financial status. " +
                "It shows available cash from all bank accounts, portfolio value from all asset accounts, " +
                "and total net worth. Below you'll find a breakdown of your accounts by type, " +
                "cash flow trends, and top spending categories.")
        ));

        // Transaction History
        accordion.add(createHelpPanel(
            getTranslation("nav.transactions", "Transaction History"),
            getTranslation("help.transactions", "View and manage all your transactions. " +
                "You can filter by account, date range, type, and search for specific transactions. " +
                "Use the 'Add Transaction' button to record new transactions. " +
                "Click on any transaction to view details or edit it. " +
                "You can sort transactions by clicking on column headers.")
        ));

        // Scheduled Transactions
        accordion.add(createHelpPanel(
            "Scheduled Transactions",
            "Create and manage recurring transactions that are automatically created on a schedule. " +
            "This is useful for regular income, bills, and subscriptions. " +
            "You can set the frequency (daily, weekly, monthly, yearly) and the system will generate transactions automatically."
        ));

        // Statistics
        accordion.add(createHelpPanel(
            getTranslation("nav.statistics", "Statistics"),
            getTranslation("help.statistics", "Analyze your financial data with detailed charts and reports. " +
                "View income and expenses by account, category, payee, or tag. " +
                "Choose different time frames (daily, weekly, monthly, yearly, or custom) to analyze your spending patterns. " +
                "Use the filters to focus on specific aspects of your finances.")
        ));

        // Vehicles
        accordion.add(createHelpPanel(
            getTranslation("nav.vehicles", "Vehicles"),
            getTranslation("help.vehicles", "Track vehicle-related expenses and fuel consumption. " +
                "Add fuel purchase transactions with memo format: 'd=XXXXXX v~XX.XX (€XX.XX XX.XXL XXXXXXkm)' " +
                "where d= is total kilometers and v~ is liters fueled. " +
                "The system will calculate distance between fill-ups, consumption, and total statistics.")
        ));

        // Manage Accounts
        accordion.add(createHelpPanel(
            getTranslation("nav.manage_accounts", "Manage Accounts"),
            getTranslation("help.manage_accounts", "Create and manage your financial accounts (bank accounts, credit cards, assets, etc.). " +
                "You can set start balances, organize accounts by type and group, and choose which accounts to include in summaries and reports. " +
                "Each account tracks its current balance automatically based on transactions.")
        ));

        // Payees
        accordion.add(createHelpPanel(
            getTranslation("nav.payees", "Payees"),
            getTranslation("help.payees", "Manage the list of people or companies you pay or receive money from. " +
                "You can assign a default category to each payee to speed up transaction entry. " +
                "When you select a payee in a transaction, the default category will be pre-selected.")
        ));

        // Categories
        accordion.add(createHelpPanel(
            getTranslation("nav.categories", "Categories"),
            getTranslation("help.categories", "Organize your income and expenses into categories. " +
                "You can create parent categories and subcategories for better organization. " +
                "Categories help you analyze your spending patterns and create budgets. " +
                "Each category is designated as either Income or Expense.")
        ));

        // Tags
        accordion.add(createHelpPanel(
            getTranslation("nav.tags", "Tags"),
            getTranslation("help.tags", "Create tags to add additional labels to your transactions. " +
                "Unlike categories, you can assign multiple tags to a single transaction. " +
                "Use tags for cross-cutting concerns like 'Work', 'Personal', 'Tax Deductible', etc.")
        ));

        // Currencies
        accordion.add(createHelpPanel(
            getTranslation("nav.currencies", "Currencies"),
            getTranslation("help.currencies", "Manage currencies and their exchange rates. " +
                "The system supports multiple currencies and automatically converts amounts for reporting. " +
                "You can update exchange rates manually or they can be fetched automatically if configured.")
        ));

        // Assets
        accordion.add(createHelpPanel(
            getTranslation("nav.assets", "Asset Management"),
            getTranslation("help.assets", "Track investments like stocks, ETFs, and cryptocurrencies. " +
                "Add assets with their symbols (e.g., VWCE.DE, AMZN, BTC-EUR) and the system can fetch current prices. " +
                "When you buy assets through transactions, the system tracks quantities and calculates unrealized gains/losses.")
        ));

        // Settings - User
        accordion.add(createHelpPanel(
            getTranslation("settings.user_title", "User Settings"),
            getTranslation("help.settings_user", "Customize your personal settings including language, password, and dark mode preferences. " +
                "You can also delete your profile here (this will permanently remove all your data).")
        ));

        // Settings - Import/Export
        accordion.add(createHelpPanel(
            getTranslation("settings.import_export_title", "Import/Export"),
            getTranslation("help.settings_import", "Import transactions from bank CSV files or export all your data to JSON format. " +
                "Supported formats include Trade Republic CSV and Homebank XHB files. " +
                "JSON export includes all your accounts, transactions, categories, payees, and settings.")
        ));

        add(accordion);
    }

    private AccordionPanel createHelpPanel(String title, String content) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);

        Paragraph description = new Paragraph(content);
        layout.add(description);

        AccordionPanel panel = new AccordionPanel(title, layout);
        return panel;
    }
}
