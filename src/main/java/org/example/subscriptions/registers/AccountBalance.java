package org.example.subscriptions.registers;

import lombok.Getter;
import lombok.Setter;
import org.example.subscriptions.catalogs.Client;
import su.onno.annotations.AccumulationRegister;
import su.onno.annotations.Dimension;
import su.onno.annotations.Resource;
import su.onno.model.AccumulationRecord;
import su.onno.model.AccumulationType;
import su.onno.types.Ref;

import java.math.BigDecimal;

@AccumulationRegister(name = "AccountBalance", title = "Остатки лицевых счетов",
        type = AccumulationType.BALANCE, allowNegative = false)
@Getter
@Setter
public class AccountBalance extends AccumulationRecord {

    @Dimension(displayName = "Клиент")
    private Ref<Client> client;

    @Resource(displayName = "Остаток")
    private BigDecimal amount;
}
