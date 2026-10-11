package org.example.subscriptions.services;

import lombok.RequiredArgsConstructor;
import org.example.subscriptions.enums.SubscriptionStatus;
import org.example.subscriptions.repositories.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import su.onno.posting.PostingService;
import su.onno.validation.ValidationException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionCancellationService {
    private final SubscriptionRepository subscriptions;
    private final PostingService posting;

    @Transactional
    public void cancel(UUID id, String reason) {
        if (id == null) {
            throw new ValidationException("Выберите подписку");
        }
        var subscription = subscriptions.findLockedByIdAndDeletionMarkFalse(id)
                .orElseThrow(() -> new ValidationException("Подписка не найдена"));
        if (subscription.getStatus() == SubscriptionStatus.CANCELLED) {
            throw new ValidationException("Подписка уже отменена");
        }
        if (subscription.isPosted()) {
            posting.unpost(subscription);
            subscription = subscriptions.findLockedByIdAndDeletionMarkFalse(id).orElseThrow();
        }
        subscription.cancel(reason);
        subscriptions.save(subscription);
    }
}
