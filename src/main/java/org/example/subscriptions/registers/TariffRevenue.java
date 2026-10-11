package org.example.subscriptions.registers;

import lombok.Getter;
import lombok.Setter;
import org.example.subscriptions.catalogs.Client;
import org.example.subscriptions.catalogs.Tariff;
import su.onno.annotations.AccumulationRegister;
import su.onno.annotations.Dimension;
import su.onno.annotations.Resource;
import su.onno.model.AccumulationRecord;
import su.onno.model.AccumulationType;
import su.onno.types.Ref;

import java.math.BigDecimal;

@AccumulationRegister(name = "TariffRevenue", title = "Выручка по тарифам", type = AccumulationType.TURNOVER)
@Getter
@Setter
public class TariffRevenue extends AccumulationRecord {
    @Dimension(displayName = "Тариф")
    private Ref<Tariff> tariff;

    @Dimension(displayName = "Клиент")
    private Ref<Client> client;

    @Resource(displayName = "Выручка")
    private BigDecimal amount;

    @Resource(displayName = "Периодов", scale = 0)
    private BigDecimal periods;
}
