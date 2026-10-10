package org.example.subscriptions.ui;

import org.springframework.stereotype.Component;
import org.example.subscriptions.catalogs.Tariff;
import su.onno.ui.EntityConfigBuilder;
import su.onno.ui.EntityView;
import su.onno.ui.ListSpec;

@Component
public class TariffView implements EntityView<Tariff> {
    @Override
    public Class<Tariff> entity() {
        return Tariff.class;
    }

    @Override
    public void list(ListSpec<Tariff> list) {
        list.columns(Tariff::getCode, Tariff::getDescription, Tariff::getPrice,
                        Tariff::getPeriodDays, Tariff::getAvailable)
                .label(Tariff::getCode, "Код").label(Tariff::getDescription, "Тариф")
                .label(Tariff::getPrice, "Цена за период").label(Tariff::getPeriodDays, "Дней в периоде")
                .label(Tariff::getAvailable, "Доступен")
                .sortBy(Tariff::getDescription, false);
    }

    @Override
    public void fields(EntityConfigBuilder<Tariff> fields) {
        fields.field(Tariff::getDescription).order(0).label("Тариф").width("full")
                .hint("Понятное название тарифного плана")
                .field(Tariff::getPrice).order(1).width("half").format("currency:RUB")
                .hint("Стоимость одного периода в рублях; бесплатный тариф имеет цену 0")
                .field(Tariff::getPeriodDays).order(2).width("half")
                .hint("Положительное целое число дней в одном периоде")
                .field(Tariff::getAvailable).order(3).width("full")
                .hint("Можно ли подключать этот тариф в новых подписках")
                .field(Tariff::getCode).order(4).label("Код").hideInForm();
    }
}
