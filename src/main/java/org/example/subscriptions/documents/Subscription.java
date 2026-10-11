package org.example.subscriptions.documents;

import lombok.Getter;
import lombok.Setter;
import org.example.subscriptions.catalogs.Client;
import org.example.subscriptions.enums.SubscriptionStatus;
import org.example.subscriptions.registers.AccountBalance;
import org.example.subscriptions.registers.TariffRevenue;
import org.example.subscriptions.services.TariffLookup;
import org.springframework.data.annotation.Transient;
import su.onno.annotations.Attribute;
import su.onno.annotations.Document;
import su.onno.annotations.TabularSection;
import su.onno.lifecycle.BeforePostHandler;
import su.onno.lifecycle.BeforeWriteHandler;
import su.onno.lifecycle.OnFillingHandler;
import su.onno.lifecycle.Postable;
import su.onno.model.DocumentObject;
import su.onno.posting.PostingContext;
import su.onno.rules.BusinessRule;
import su.onno.rules.Validated;
import su.onno.types.Ref;
import su.onno.validation.ValidationException;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(name = "Subscriptions", title = "Подписки", numberPrefix = "SUB-")
public class Subscription extends DocumentObject
        implements OnFillingHandler, BeforeWriteHandler, BeforePostHandler, Validated, Postable {
    @Getter
    @Setter
    @Attribute(displayName = "Клиент", required = true)
    private Ref<Client> client;

    @Getter
    @Setter
    @Attribute(displayName = "Статус")
    private SubscriptionStatus status;

    @Getter
    @Setter
    @Attribute(displayName = "Дата начала")
    private LocalDate startDate;

    @Getter
    @Setter
    @Attribute(displayName = "Дата окончания")
    private LocalDate endDate;

    @Getter
    @Setter
    @Attribute(displayName = "Итого")
    private BigDecimal total;

    @Getter
    @Attribute(displayName = "Причина отмены", length = 1000)
    private String cancellationReason;

    @Getter
    @TabularSection
    private List<SubscriptionLine> lines = new ArrayList<>();

    @Transient
    private boolean tariffsAvailable;

    @Transient
    private SubscriptionSnapshot persistedPurchase;

    @Transient
    private SubscriptionStatus persistedStatus;

    @Transient
    private LocalDate statusRefreshDate;

    @Transient
    private boolean cancellationRequested;

    @Override
    public void onFilling() {
        if (getDate() == null) {
            setDate(LocalDateTime.now());
        }
        if (startDate == null) {
            startDate = LocalDate.now();
        }
        if (status == null) {
            status = SubscriptionStatus.DRAFT;
        }
    }

    @Override
    public void beforeWrite() {
        if (statusRefreshDate != null || cancellationRequested) {
            if (!isStateOnlyUpdate()) {
                throw new ValidationException("При изменении состояния нельзя изменять данные покупки");
            }
            return;
        }
        if (isPosted()) {
            return;
        }
        total = BigDecimal.ZERO;
        endDate = null;
        tariffsAvailable = true;
        long longestDays = 0;
        if (lines != null) {
            for (SubscriptionLine line : lines) {
                if (line == null) {
                    tariffsAvailable = false;
                    continue;
                }
                var tariff = TariffLookup.find(line.getTariff()).orElse(null);
                if (tariff == null || !Boolean.TRUE.equals(tariff.getAvailable())) {
                    tariffsAvailable = false;
                }
                line.setPrice(tariff == null ? BigDecimal.ZERO : tariff.getPrice());
                line.setAmount(BigDecimal.ZERO);
                if (tariff != null && line.getPeriods() != null && line.getPeriods() > 0) {
                    line.setAmount(line.getPrice().multiply(BigDecimal.valueOf(line.getPeriods())));
                    total = total.add(line.getAmount());
                    longestDays = Math.max(longestDays, (long) tariff.getPeriodDays() * line.getPeriods());
                }
            }
        }
        if (startDate != null) {
            try {
                endDate = startDate.plusDays(longestDays);
            } catch (DateTimeException ignored) {
                // The business rule below reports an invalid duration without a server error.
            }
        }
        if (status != null && status != SubscriptionStatus.CANCELLED) {
            status = SubscriptionStatus.DRAFT;
        }
    }

    @Override
    public void beforePost() {
        if (isPosted()) {
            throw new ValidationException("Сначала снимите проведение подписки");
        }
        if (startDate != null && endDate != null) {
            status = statusOn(LocalDate.now(), true);
        }
    }

    /** Called on a repository-loaded document while holding its database row lock. */
    public boolean refreshStatus(LocalDate today) {
        if (isDeletionMark() || status == SubscriptionStatus.CANCELLED || startDate == null || endDate == null) {
            return false;
        }
        SubscriptionStatus next = statusOn(today, isPosted());
        if (status == next) {
            return false;
        }
        if (persistedPurchase == null || !persistedPurchase.matches(this)) {
            throw new ValidationException("Перед обновлением статуса загрузите неизменённую подписку из репозитория");
        }
        statusRefreshDate = today;
        status = next;
        return true;
    }

    private SubscriptionStatus statusOn(LocalDate today, boolean posted) {
        if (status == SubscriptionStatus.CANCELLED) {
            return SubscriptionStatus.CANCELLED;
        }
        if (!posted) {
            return SubscriptionStatus.DRAFT;
        }
        return today.isBefore(startDate) ? SubscriptionStatus.SCHEDULED
                : today.isBefore(endDate) ? SubscriptionStatus.ACTIVE : SubscriptionStatus.EXPIRED;
    }

    // Repository callbacks remember what was read/saved. Generic HTTP updates cannot enable this path.
    void rememberPersistedState() {
        persistedPurchase = SubscriptionSnapshot.capture(this);
        persistedStatus = status;
        statusRefreshDate = null;
        cancellationRequested = false;
    }

    public void cancel(String reason) {
        if (isPosted() || isDeletionMark() || status == SubscriptionStatus.CANCELLED
                || persistedPurchase == null || !persistedPurchase.matches(this)) {
            throw new ValidationException("Для отмены загрузите подписку и снимите её проведение");
        }
        String normalized = reason == null ? "" : reason.strip();
        if (normalized.isEmpty() || normalized.length() > 1000) {
            throw new ValidationException("Укажите причину отмены: от 1 до 1000 символов");
        }
        cancellationReason = normalized;
        status = SubscriptionStatus.CANCELLED;
        cancellationRequested = true;
    }

    private boolean isStateOnlyUpdate() {
        return isStatusOnlyUpdate() || isCancellationUpdate();
    }

    private boolean isCancellationUpdate() {
        return cancellationRequested && persistedPurchase != null
                && persistedStatus != SubscriptionStatus.CANCELLED
                && !isPosted() && !isDeletionMark() && status == SubscriptionStatus.CANCELLED
                && persistedPurchase.matchesIgnoringCancellationReason(this)
                && cancellationReason != null && !cancellationReason.isBlank() && cancellationReason.length() <= 1000;
    }

    private boolean isStatusOnlyUpdate() {
        return statusRefreshDate != null && persistedPurchase != null
                && persistedStatus != SubscriptionStatus.CANCELLED && status != SubscriptionStatus.CANCELLED
                && !isDeletionMark() && startDate != null && endDate != null
                && persistedPurchase.matches(this)
                && status == statusOn(statusRefreshDate, isPosted());
    }

    @Override
    public List<BusinessRule> rules() {
        return List.of(
                new BusinessRule("subscription-unposted", "Сначала снимите проведение подписки", () -> !isPosted() || isStateOnlyUpdate()),
                BusinessRule.onField("client", "Выберите клиента", () -> client != null),
                BusinessRule.onField("status", "Выберите статус", () -> status != null),
                BusinessRule.onField("startDate", "Укажите дату начала", () -> startDate != null),
                new BusinessRule("lines-required", "Добавьте хотя бы один тариф",
                        () -> lines != null && !lines.isEmpty()),
                new BusinessRule("periods-positive", "Число периодов в каждой строке должно быть больше нуля",
                        () -> lines != null && lines.stream().allMatch(line -> line != null
                                && line.getPeriods() != null && line.getPeriods() > 0)),
                new BusinessRule("tariffs-available", "Все тарифы должны существовать и быть доступны для подключения",
                        () -> isStateOnlyUpdate() || tariffsAvailable),
                new BusinessRule("duration-valid", "Срок подписки выходит за допустимый диапазон дат",
                        () -> endDate != null),
                new BusinessRule("total-valid", "Сумма подписки слишком велика",
                        () -> total != null && total.compareTo(new BigDecimal("9999999999999.99")) <= 0)
        );
    }

    @Override
    public void handlePosting(PostingContext context) {
        if (status == SubscriptionStatus.CANCELLED) {
            return;
        }
        context.movements(AccountBalance.class).addExpense(movement -> {
            movement.setClient(client);
            movement.setAmount(total);
        });
        for (SubscriptionLine line : lines) {
            context.movements(TariffRevenue.class).addReceipt(movement -> {
                movement.setClient(client);
                movement.setTariff(line.getTariff());
                movement.setAmount(line.getAmount());
                movement.setPeriods(BigDecimal.valueOf(line.getPeriods()));
            });
        }
    }
}
