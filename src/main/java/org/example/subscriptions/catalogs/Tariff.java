package org.example.subscriptions.catalogs;

import su.onno.annotations.Attribute;
import su.onno.annotations.Catalog;
import su.onno.lifecycle.OnFillingHandler;
import su.onno.model.CatalogObject;
import su.onno.rules.BusinessRule;
import su.onno.rules.Validated;

import java.math.BigDecimal;
import java.util.List;

@Catalog(name = "Tariffs", title = "Тарифы", codePrefix = "TR-")
public class Tariff extends CatalogObject implements OnFillingHandler, Validated {

    @Attribute(displayName = "Цена за период", required = true, min = 0)
    private BigDecimal price;

    @Attribute(displayName = "Длительность периода, дней", required = true, min = 1)
    private Integer periodDays;

    @Attribute(displayName = "Доступен для подключения")
    private Boolean available;

    @Override
    public void onFilling() {
        if (available == null) {
            available = true;
        }
    }

    @Override
    public List<BusinessRule> rules() {
        return List.of(
                BusinessRule.onField("description", "Укажите название тарифа",
                        () -> getDescription() != null && !getDescription().isBlank()),
                BusinessRule.onField("price", "Цена должна быть неотрицательной и содержать не более двух знаков после запятой",
                        () -> price != null && price.signum() >= 0 && price.stripTrailingZeros().scale() <= 2),
                BusinessRule.onField("periodDays", "Длительность периода должна быть больше нуля",
                        () -> periodDays != null && periodDays > 0),
                BusinessRule.onField("available", "Укажите доступность тарифа", () -> available != null)
        );
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getPeriodDays() {
        return periodDays;
    }

    public Boolean getAvailable() {
        return available;
    }
}
