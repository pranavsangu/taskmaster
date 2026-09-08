package com.taskmaster;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.taskmaster.testsupport.AuditableTestEntity;
import com.taskmaster.testsupport.AuditableTestEntityRepository;
import com.taskmaster.config.JpaConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaConfig.class)
class AuditingRepositoryTest {

    @Autowired
    private AuditableTestEntityRepository repository;

    @Test
    void persistsCreatedAndUpdatedTimestamps() {
        AuditableTestEntity saved = repository.saveAndFlush(new AuditableTestEntity("baseline"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isInstanceOf(Instant.class);
    }
}
