package org.example.subscriptions.catalogs;

import org.example.subscriptions.enums.ClientStatus;
import su.onno.annotations.Attribute;
import su.onno.annotations.Catalog;
import su.onno.lifecycle.OnFillingHandler;
import su.onno.model.CatalogObject;
import su.onno.rules.BusinessRule;
import su.onno.rules.Validated;

import java.time.LocalDate;
import java.util.List;

@Catalog(name = "Clients", title = "Клиенты", codePrefix = "CL-")
public class Client extends CatalogObject implements OnFillingHandler, Validated {

    @Attribute(displayName = "Статус")
    private ClientStatus status;

    @Attribute(displayName = "Электронная почта", length = 254, email = true)
    private String email;

    @Attribute(displayName = "Телефон", length = 50)
    private String phone;

    @Attribute(displayName = "Дата регистрации")
    private LocalDate registrationDate;

    @Override
    public void onFilling() {
        if (status == null) {
            status = ClientStatus.ACTIVE;
        }
        if (registrationDate == null) {
            registrationDate = LocalDate.now();
        }
    }

    @Override
    public List<BusinessRule> rules() {
        return List.of(
                BusinessRule.onField("description", "Укажите имя или название клиента",
                        () -> getDescription() != null && !getDescription().isBlank()),
                BusinessRule.onField("status", "Выберите статус клиента", () -> status != null),
                BusinessRule.onField("registrationDate", "Укажите дату регистрации",
                        () -> registrationDate != null)
        );
    }

    public ClientStatus getStatus() {
        return status;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public LocalDate getRegistrationDate() {
        return registrationDate;
    }
}
