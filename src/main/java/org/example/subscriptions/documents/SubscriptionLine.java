package org.example.subscriptions.documents;

import lombok.Getter;
import lombok.Setter;
import org.example.subscriptions.catalogs.Tariff;
import su.onno.annotations.Attribute;
import su.onno.model.TabularSectionRow;
import su.onno.types.Ref;

import java.math.BigDecimal;

@Getter
public class SubscriptionLine extends TabularSectionRow {
    @Attribute(displayName = "Тариф", required = true)
    private Ref<Tariff> tariff;

    @Setter
    @Attribute(displayName = "Периодов", required = true, min = 1)
    private Integer periods;

    @Setter
    @Attribute(displayName = "Цена за период")
    private BigDecimal price;

    @Setter
    @Attribute(displayName = "Сумма")
    private BigDecimal amount;
}
