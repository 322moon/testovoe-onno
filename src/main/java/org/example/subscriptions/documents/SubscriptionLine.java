package org.example.subscriptions.documents;

import org.example.subscriptions.catalogs.Tariff;
import su.onno.annotations.Attribute;
import su.onno.model.TabularSectionRow;
import su.onno.types.Ref;

import java.math.BigDecimal;

public class SubscriptionLine extends TabularSectionRow {
    @Attribute(displayName = "Тариф", required = true)
    private Ref<Tariff> tariff;

    @Attribute(displayName = "Периодов", required = true, min = 1)
    private Integer periods;

    @Attribute(displayName = "Цена за период")
    private BigDecimal price;

    @Attribute(displayName = "Сумма")
    private BigDecimal amount;

    public Ref<Tariff> getTariff() {
        return tariff;
    }

    public Integer getPeriods() {
        return periods;
    }

    public void setPeriods(Integer periods) {
        this.periods = periods;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
