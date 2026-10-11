package org.example.subscriptions.ui;

import org.example.subscriptions.documents.Payment;
import org.example.subscriptions.documents.Subscription;
import org.example.subscriptions.registers.TariffRevenue;
import org.springframework.stereotype.Component;
import su.onno.ui.Page;
import su.onno.ui.PageBuilder;

@Component
public class DashboardPage implements Page {
    @Override
    public String route() {
        return "/dashboard";
    }

    @Override
    public void compose(PageBuilder page) {
        page.title("Дашборд");
        page.widget("Период").type("timeRange").width("full").order(0)
                .config("presets", "7d,30d,90d,1y,all").config("default", "30d");

        page.widget("Поступления").type("stat").width("1/3").order(1).document(Payment.class)
                .dateField(Payment::getDate).metricField(Payment::getAmount)
                .config("metric", "sum").config("filter", "_posted = true")
                .config("currency", "RUB").config("locale", "ru-RU").config("trend", "false")
                .hint("Сумма проведённых платежей за выбранный период");
        page.widget("Выручка").type("stat").width("1/3").order(2).register(TariffRevenue.class)
                .metricField(TariffRevenue::getAmount).config("metric", "sum")
                .config("currency", "RUB").config("locale", "ru-RU").config("trend", "false")
                .hint("Выручка по движениям подписок за выбранный период; отмена убирает её из отчёта");
        page.widget("Подключённые периоды").type("stat").width("1/3").order(3).register(TariffRevenue.class)
                .metricField(TariffRevenue::getPeriods).config("metric", "sum")
                .config("format", "integer").config("locale", "ru-RU").config("trend", "false");

        page.chart("Поступления по датам, ₽", Payment.class).width("1/2").order(4)
                .time(Payment::getDate).sum(Payment::getAmount).bar().locale("ru-RU")
                .filter("_posted = true");
        page.widget("Выручка по тарифам").type("chart").width("1/2").order(5).register(TariffRevenue.class)
                .metricField(TariffRevenue::getAmount).config("metric", "sum")
                .config("groupBy", "tariff_display").config("kind", "donut")
                .config("currency", "RUB").config("locale", "ru-RU");

        page.widget("Последние платежи").type("list").width("1/2").order(6).document(Payment.class)
                .dateField(Payment::getDate).maxItems(5)
                .config("titleTemplate", "{number} · {clientDisplay}")
                .config("secondaryField", "methodDisplay").config("currency", "RUB").config("locale", "ru-RU")
                .hint("Пять последних платежей за всё время, включая черновики");
        page.widget("Последние подписки").type("list").width("1/2").order(7).document(Subscription.class)
                .dateField(Subscription::getDate).maxItems(5)
                .config("titleTemplate", "{number} · {clientDisplay}")
                .config("secondaryField", "statusDisplay").config("currency", "RUB").config("locale", "ru-RU")
                .hint("Пять последних подписок за всё время, включая черновики и отменённые");
    }
}
