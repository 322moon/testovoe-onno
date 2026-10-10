package org.example.subscriptions.ui;

import org.springframework.stereotype.Component;
import org.example.subscriptions.registers.AccountBalance;
import su.onno.ui.EntityConfigBuilder;
import su.onno.ui.EntityView;
import su.onno.ui.ListSpec;

@Component
public class AccountBalanceView implements EntityView<AccountBalance> {
    @Override
    public Class<AccountBalance> entity() {
        return AccountBalance.class;
    }

    @Override
    public void list(ListSpec<AccountBalance> list) {
        list.columns(AccountBalance::getClient, AccountBalance::getAmount)
                .label(AccountBalance::getClient, "Клиент").label(AccountBalance::getAmount, "Остаток");
    }

    @Override
    public void fields(EntityConfigBuilder<AccountBalance> fields) {
        fields.field(AccountBalance::getAmount).format("currency:RUB")
                .field(AccountBalance::getPeriod).format("dd.MM.yyyy HH:mm");
    }
}
