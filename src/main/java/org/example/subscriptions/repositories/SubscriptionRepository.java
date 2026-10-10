package org.example.subscriptions.repositories;

import org.example.subscriptions.documents.Subscription;
import org.springframework.data.relational.core.sql.LockMode;
import org.springframework.data.relational.repository.Lock;
import su.onno.repository.DocumentRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends DocumentRepository<Subscription> {
    @Lock(LockMode.PESSIMISTIC_WRITE)
    Optional<Subscription> findLockedByIdAndDeletionMarkFalse(UUID id);
}
