package org.example.subscriptions.ui;

import org.example.subscriptions.registers.TariffRevenue;
import org.springframework.stereotype.Component;
import su.onno.ui.EntityConfigBuilder;
import su.onno.ui.EntityView;
import su.onno.ui.ListSpec;

@Component
public class TariffRevenueView implements EntityView<TariffRevenue> {
    @Override
    public Class<TariffRevenue> entity() {
        return TariffRevenue.class;
    }

    @Override
    public void list(ListSpec<TariffRevenue> list) {
        list.columns(TariffRevenue::getClient, TariffRevenue::getTariff,
                TariffRevenue::getAmount, TariffRevenue::getPeriods)
                .label(TariffRevenue::getClient, "Клиент").label(TariffRevenue::getTariff, "Тариф")
                .label(TariffRevenue::getAmount, "Выручка").label(TariffRevenue::getPeriods, "Периодов");
    }

    @Override
    public void fields(EntityConfigBuilder<TariffRevenue> fields) {
        fields.field(TariffRevenue::getAmount).format("currency:RUB")
                .field(TariffRevenue::getPeriods).format("integer")
                .field(TariffRevenue::getPeriod).format("dd.MM.yyyy HH:mm");
    }
}
