package org.example.subscriptions.services;

import jakarta.annotation.PreDestroy;
import org.example.subscriptions.catalogs.Tariff;
import org.example.subscriptions.repositories.TariffRepository;
import org.springframework.stereotype.Component;
import su.onno.types.Ref;

import java.util.Optional;

@Component
public class TariffLookup {
    private static volatile TariffLookup instance;
    private final TariffRepository tariffs;

    public TariffLookup(TariffRepository tariffs) {
        this.tariffs = tariffs;
        instance = this;
    }

    public static Optional<Tariff> find(Ref<Tariff> ref) {
        if (ref == null) {
            return Optional.empty();
        }
        TariffLookup lookup = instance;
        if (lookup == null) {
            throw new IllegalStateException("TariffLookup has not been initialized");
        }
        return lookup.tariffs.findActiveById(ref.id());
    }

    @PreDestroy
    void clear() {
        if (instance == this) {
            instance = null;
        }
    }
}
