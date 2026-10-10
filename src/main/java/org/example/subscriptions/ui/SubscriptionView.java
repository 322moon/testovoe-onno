package org.example.subscriptions.ui;

import org.example.subscriptions.documents.Subscription;
import org.example.subscriptions.documents.SubscriptionLine;
import org.springframework.stereotype.Component;
import su.onno.ui.EntityConfigBuilder;
import su.onno.ui.EntityView;
import su.onno.ui.ListSpec;

@Component
public class SubscriptionView implements EntityView<Subscription> {
    @Override
    public Class<Subscription> entity() {
        return Subscription.class;
    }

    @Override
    public void list(ListSpec<Subscription> list) {
        list.columns(Subscription::getNumber, Subscription::getClient, Subscription::getStatus,
                        Subscription::getStartDate, Subscription::getEndDate, Subscription::getTotal,
                        Subscription::isPosted)
                .label(Subscription::getNumber, "Номер").label(Subscription::isPosted, "Проведена")
                .label(Subscription::getClient, "Клиент").label(Subscription::getStatus, "Статус")
                .label(Subscription::getStartDate, "Начало").label(Subscription::getEndDate, "Окончание")
                .label(Subscription::getTotal, "Итого")
                .sortBy(Subscription::getDate, true);
        list.filter(Subscription::getStatus).multiOptions();
    }

    @Override
    public void fields(EntityConfigBuilder<Subscription> fields) {
        fields.action("unpost").primary();
        fields.field(Subscription::getClient).order(0).width("full").hint("Владелец лицевого счёта")
                .field(Subscription::getStatus).order(1).width("half")
                .hint("Для отмены сначала снимите проведение, затем выберите «Отменена»")
                .field(Subscription::getStartDate).order(2).width("half").format("dd.MM.yyyy")
                .hint("Первый день действия подписки")
                .field(Subscription::getEndDate).order(3).width("half").format("dd.MM.yyyy")
                .hint("Рассчитывается при сохранении по самой длинной строке; этот день уже не входит в срок")
                .field(Subscription::getTotal).order(4).width("half").format("currency:RUB")
                .hint("Рассчитывается при сохранении; списывается при проведении")
                .field(Subscription::getDate).order(5).width("half").label("Дата документа").format("dd.MM.yyyy HH:mm")
                .field(Subscription::getNumber).label("Номер").hideInForm()
                .field(Subscription::isPosted).label("Проведена").hideInForm();
        fields.rowRefField(Subscription::getLines, SubscriptionLine::getTariff).order(0).hint("Доступный для подключения тариф");
        fields.rowField(Subscription::getLines, SubscriptionLine::getPeriods).order(1).format("integer")
                .hint("Положительное целое число периодов");
        fields.rowField(Subscription::getLines, SubscriptionLine::getPrice).order(2).format("currency:RUB")
                .hint("Подставляется из тарифа при сохранении и проведении");
        fields.rowField(Subscription::getLines, SubscriptionLine::getAmount).order(3).format("currency:RUB")
                .hint("Цена за период, умноженная на число периодов; рассчитывается сервером");
    }
}
