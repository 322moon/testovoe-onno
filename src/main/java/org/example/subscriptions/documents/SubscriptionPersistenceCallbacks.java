package org.example.subscriptions.documents;

import org.springframework.data.relational.core.mapping.event.AfterConvertCallback;
import org.springframework.data.relational.core.mapping.event.AfterSaveCallback;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionPersistenceCallbacks
        implements AfterConvertCallback<Subscription>, AfterSaveCallback<Subscription> {
    @Override
    public Subscription onAfterConvert(Subscription subscription) {
        subscription.rememberPersistedState();
        return subscription;
    }

    @Override
    public Subscription onAfterSave(Subscription subscription) {
        subscription.rememberPersistedState();
        return subscription;
    }
}
