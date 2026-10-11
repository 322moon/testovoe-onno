package org.example.subscriptions.documents;

import lombok.Getter;
import org.example.subscriptions.catalogs.Client;
import org.example.subscriptions.enums.PaymentMethod;
import org.example.subscriptions.registers.AccountBalance;
import su.onno.annotations.Attribute;
import su.onno.annotations.Document;
import su.onno.lifecycle.OnFillingHandler;
import su.onno.lifecycle.Postable;
import su.onno.model.DocumentObject;
import su.onno.posting.PostingContext;
import su.onno.rules.BusinessRule;
import su.onno.rules.Validated;
import su.onno.types.Ref;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Document(name = "Payments", title = "Платежи", numberPrefix = "PAY-")
@Getter
public class Payment extends DocumentObject implements OnFillingHandler, Validated, Postable {

    @Attribute(displayName = "Клиент", required = true)
    private Ref<Client> client;

    @Attribute(displayName = "Сумма", required = true, min = 0.01)
    private BigDecimal amount;

    @Attribute(displayName = "Способ оплаты")
    private PaymentMethod method;

    @Override
    public void onFilling() {
        if (getDate() == null) {
            setDate(LocalDateTime.now());
        }
        if (method == null) {
            method = PaymentMethod.CARD;
        }
    }

    @Override
    public List<BusinessRule> rules() {
        return List.of(
                new BusinessRule("payment-unposted", "Сначала снимите проведение платежа", () -> !isPosted()),
                BusinessRule.onField("client", "Выберите клиента", () -> client != null),
                BusinessRule.onField("amount", "Сумма должна быть больше нуля и содержать не более двух знаков после запятой",
                        () -> amount != null && amount.signum() > 0 && amount.stripTrailingZeros().scale() <= 2),
                BusinessRule.onField("method", "Выберите способ оплаты", () -> method != null)
        );
    }

    @Override
    public void handlePosting(PostingContext context) {
        context.movements(AccountBalance.class).addReceipt(movement -> {
            movement.setClient(client);
            movement.setAmount(amount);
        });
    }
}
