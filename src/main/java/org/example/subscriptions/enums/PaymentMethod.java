package org.example.subscriptions.enums;

import su.onno.annotations.EnumLabel;
import su.onno.annotations.Enumeration;

@Enumeration(name = "PaymentMethods", title = "Способы оплаты")
public enum PaymentMethod {
    @EnumLabel("Банковская карта")
    CARD,

    @EnumLabel("СБП")
    SBP
}
