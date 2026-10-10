package org.example.subscriptions.jobs;

import org.example.subscriptions.repositories.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import su.onno.annotations.ScheduledJob;
import su.onno.jobs.BackgroundTask;

import java.time.LocalDate;

@Component
@ScheduledJob(name = "subscription-statuses", cron = "* * * * *")
public class SubscriptionStatusJob implements BackgroundTask {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionStatusJob.class);
    private final SubscriptionRepository subscriptions;

    private final TransactionTemplate transaction;

    public SubscriptionStatusJob(SubscriptionRepository subscriptions, PlatformTransactionManager transactionManager) {
        this.subscriptions = subscriptions;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public void execute() {
        int changed = refresh(LocalDate.now());
        if (changed > 0) {
            log.info("Updated {} subscription statuses", changed);
        }
    }

    public int refresh(LocalDate today) {
        int changed = 0;
        for (var candidate : subscriptions.findAllActive()) {
            boolean updated = Boolean.TRUE.equals(transaction.execute(ignored ->
                    subscriptions.findLockedByIdAndDeletionMarkFalse(candidate.getId())
                            .map(subscription -> {
                                if (!subscription.refreshStatus(today)) {
                                    return false;
                                }
                                subscriptions.save(subscription);
                                return true;
                            }).orElse(false)));
            if (updated) {
                changed++;
            }
        }
        return changed;
    }
}
