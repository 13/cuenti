package com.cuenti.homebanking.views.help;

import com.vaadin.flow.component.accordion.Accordion;
import com.vaadin.flow.component.accordion.AccordionPanel;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.vaadin.lineawesome.LineAwesomeIconUrl;

@PageTitle("Help")
@Route("help")
@Menu(order = 12, icon = LineAwesomeIconUrl.QUESTION_CIRCLE_SOLID)
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

        // Manage Accounts
        accordion.add(createHelpPanel(
            getTranslation("nav.manage_accounts", "Manage Accounts"),
            getTranslation("help.manage_accounts", "Create and manage your financial accounts (bank accounts, credit cards, assets, etc.). " +
                "You can set start balances, organize accounts by type and group, and choose which accounts to include in summaries and reports. " +
                "Each account tracks its current balance automatically based on transactions.")
        ));

        // Categories
        accordion.add(createHelpPanel(
            getTranslation("nav.categories", "Categories"),
            getTranslation("help.categories", "Organize your income and expenses into categories. " +
                "You can create parent categories and subcategories for better organization. " +
                "Categories help you analyze your spending patterns and create budgets. " +
                "Each category is designated as either Income or Expense.")
        ));

        // Payees
        accordion.add(createHelpPanel(
            getTranslation("nav.payees", "Payees"),
            getTranslation("help.payees", "Manage the list of people or companies you pay or receive money from. " +
                "You can assign a default category to each payee to speed up transaction entry. " +
                "When you select a payee in a transaction, the default category will be pre-selected.")
        ));

        // Tags
        accordion.add(createHelpPanel(
            getTranslation("nav.tags", "Tags"),
            getTranslation("help.tags", "Create tags to add additional labels to your transactions. " +
                "Unlike categories, you can assign multiple tags to a single transaction. " +
                "Use tags for cross-cutting concerns like 'Work', 'Personal', 'Tax Deductible', etc.")
        ));

        // Assets
        accordion.add(createHelpPanel(
            getTranslation("nav.assets", "Assets"),
            getTranslation("help.assets", "Track investments like stocks, ETFs, and cryptocurrencies. " +
                "Add assets with their symbols (e.g., VWCE.DE, AMZN, BTC-EUR) and the system can fetch current prices. " +
                "When you buy assets through transactions, the system tracks quantities and calculates unrealized gains/losses.")
        ));

        // Settings
        accordion.add(createHelpPanel(
            getTranslation("nav.settings", "Settings"),
            "Configure your account preferences including language, currency, and password settings. " +
            "Administrators can also manage users and global settings from here."
        ));

        add(accordion);
    }

    private AccordionPanel createHelpPanel(String header, String content) {
        VerticalLayout panelContent = new VerticalLayout();
        panelContent.setPadding(true);
        panelContent.setSpacing(false);

        Paragraph p = new Paragraph(content);
        p.getStyle().set("line-height", "1.6");
        panelContent.add(p);

        AccordionPanel panel = new AccordionPanel(header, panelContent);
        return panel;
    }
}
