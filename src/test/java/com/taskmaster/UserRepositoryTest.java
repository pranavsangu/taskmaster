package com.taskmaster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.taskmaster.config.JpaConfig;
import com.taskmaster.domain.User;
import com.taskmaster.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaConfig.class)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesAndFindsUserByEmail() {
        User saved = userRepository.saveAndFlush(
                new User("John Doe", "john@example.com", "bcrypt-hash-placeholder")
        );

        assertThat(userRepository.findByEmail("john@example.com"))
                .isPresent()
                .get()
                .satisfies(user -> {
                    assertThat(user.getId()).isEqualTo(saved.getId());
                    assertThat(user.getName()).isEqualTo("John Doe");
                    assertThat(user.getPassword()).isEqualTo("bcrypt-hash-placeholder");
                });
    }

    @Test
    void rejectsDuplicateEmail() {
        userRepository.saveAndFlush(new User("First User", "same@example.com", "hash-one"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(
                new User("Second User", "same@example.com", "hash-two")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
