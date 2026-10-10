package org.example.subscriptions.ui;

import org.springframework.stereotype.Component;
import org.example.subscriptions.documents.Payment;
import su.onno.ui.EntityConfigBuilder;
import su.onno.ui.EntityView;
import su.onno.ui.ListSpec;

@Component
public class PaymentView implements EntityView<Payment> {
    @Override
    public Class<Payment> entity() {
        return Payment.class;
    }

    @Override
    public void list(ListSpec<Payment> list) {
        list.columns(Payment::getNumber, Payment::getDate, Payment::getClient,
                        Payment::getAmount, Payment::getMethod, Payment::isPosted)
                .label(Payment::getNumber, "Номер").label(Payment::getDate, "Дата")
                .label(Payment::getClient, "Клиент").label(Payment::getAmount, "Сумма")
                .label(Payment::getMethod, "Способ оплаты").label(Payment::isPosted, "Проведён").sortBy(Payment::getDate, true);
    }

    @Override
    public void fields(EntityConfigBuilder<Payment> fields) {
        fields.action("unpost").primary();
        fields.field(Payment::getClient).order(0).width("full")
                .hint("Клиент, чей лицевой счёт нужно пополнить")
                .field(Payment::getAmount).order(1).width("half").format("currency:RUB")
                .hint("Деньги зачисляются только после проведения документа")
                .field(Payment::getMethod).order(2).width("half")
                .hint("Как получен платёж")
                .field(Payment::getDate).order(3).label("Дата").width("half").format("dd.MM.yyyy HH:mm")
                .hint("Дата и время документа по часам сервера")
                .field(Payment::getNumber).order(4).label("Номер").hideInForm()
                .field(Payment::isPosted).order(5).label("Проведён").hideInForm();
    }
}
