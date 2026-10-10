package org.example.subscriptions.ui;

import org.springframework.stereotype.Component;
import org.example.subscriptions.catalogs.Client;
import su.onno.ui.EntityConfigBuilder;
import su.onno.ui.EntityView;
import su.onno.ui.ListSpec;

@Component
public class ClientView implements EntityView<Client> {

    @Override
    public Class<Client> entity() {
        return Client.class;
    }

    @Override
    public void list(ListSpec<Client> list) {
        list.columns(Client::getCode, Client::getDescription, Client::getStatus,
                        Client::getEmail, Client::getPhone, Client::getRegistrationDate)
                .label(Client::getCode, "Код")
                .label(Client::getDescription, "Клиент")
                .label(Client::getStatus, "Статус").label(Client::getEmail, "Email")
                .label(Client::getPhone, "Телефон").label(Client::getRegistrationDate, "Дата регистрации")
                .sortBy(Client::getDescription, false);
    }

    @Override
    public void fields(EntityConfigBuilder<Client> fields) {
        fields.field(Client::getDescription).order(0).label("Клиент").width("full")
                .hint("Имя человека или название организации")
                .field(Client::getStatus).order(1).width("half")
                .hint("Учётный статус клиента")
                .field(Client::getRegistrationDate).order(2).width("half").format("dd.MM.yyyy")
                .hint("При создании автоматически подставляется текущая дата")
                .field(Client::getEmail).order(3).width("half")
                .placeholder("client@example.com").hint("Необязательное поле")
                .field(Client::getPhone).order(4).width("half")
                .hint("Необязательное поле, включая код страны")
                .field(Client::getCode).order(5).label("Код").hideInForm()
                .hint("Присваивается автоматически");
    }
}
