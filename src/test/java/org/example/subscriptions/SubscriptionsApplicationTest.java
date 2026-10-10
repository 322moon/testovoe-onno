package org.example.subscriptions;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import su.onno.metadata.MetadataRegistry;
import su.onno.posting.PostingService;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SubscriptionsApplicationTest {

    @Autowired
    private MetadataRegistry metadataRegistry;

    @Autowired
    private PostingService postingService;

    @Test
    void startsWithOnnoInfrastructure() {
        assertThat(metadataRegistry).isNotNull();
        assertThat(postingService).isNotNull();
    }
}
