package org.example.subscriptions.documents;

import org.example.subscriptions.catalogs.Client;
import org.example.subscriptions.catalogs.Tariff;
import su.onno.types.Ref;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

record SubscriptionSnapshot(UUID id, String number, LocalDateTime date, boolean posted, boolean deleted,
                            Ref<Client> client, LocalDate start, LocalDate end, BigDecimal total,
                            String cancellationReason, List<LineSnapshot> lines) {
    static SubscriptionSnapshot capture(Subscription subscription) {
        return capture(subscription, subscription.getCancellationReason());
    }

    boolean matches(Subscription subscription) {
        return equals(capture(subscription));
    }

    boolean matchesIgnoringCancellationReason(Subscription subscription) {
        return equals(capture(subscription, cancellationReason));
    }

    private static SubscriptionSnapshot capture(Subscription subscription, String reason) {
        List<LineSnapshot> items = subscription.getLines() == null ? null
                : subscription.getLines().stream().map(line -> line == null ? null
                : new LineSnapshot(line.getId(), line.getTariff(), line.getPeriods(), line.getPrice(), line.getAmount())).toList();
        return new SubscriptionSnapshot(subscription.getId(), subscription.getNumber(), subscription.getDate(),
                subscription.isPosted(), subscription.isDeletionMark(), subscription.getClient(),
                subscription.getStartDate(), subscription.getEndDate(), subscription.getTotal(), reason, items);
    }

    private record LineSnapshot(UUID id, Ref<Tariff> tariff, Integer periods, BigDecimal price, BigDecimal amount) {
    }
}
