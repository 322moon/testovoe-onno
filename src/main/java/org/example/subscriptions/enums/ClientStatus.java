package org.example.subscriptions.enums;

import su.onno.annotations.EnumLabel;
import su.onno.annotations.Enumeration;

@Enumeration(name = "ClientStatuses", title = "Статусы клиентов")
public enum ClientStatus {

    @EnumLabel(value = "Активен", color = "#059669")
    ACTIVE,

    @EnumLabel(value = "Неактивен", color = "#6B7280")
    INACTIVE
}
