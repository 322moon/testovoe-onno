package org.example.subscriptions.ui;

import org.springframework.stereotype.Component;
import org.example.subscriptions.catalogs.Client;
import org.example.subscriptions.catalogs.Tariff;
import org.example.subscriptions.documents.Payment;
import org.example.subscriptions.documents.Subscription;
import org.example.subscriptions.registers.TariffRevenue;
import org.example.subscriptions.registers.AccountBalance;
import su.onno.ui.Layout;
import su.onno.ui.LayoutSpec;
import su.onno.ui.NavStyle;

@Component
public class ApplicationLayout implements Layout {
    @Override
    public void configure(LayoutSpec layout) {
        layout.shell().brand("Сервис подписок").nav(NavStyle.SIDEBAR);
        layout.section("Справочники").order(0).icon("users")
                .catalog(Client.class).catalog(Tariff.class);
        layout.section("Документы").order(1).icon("file-text").document(Payment.class).document(Subscription.class);
        layout.section("Отчёты").order(2).icon("chart-column").register(AccountBalance.class).register(TariffRevenue.class);
    }
}
