package com.taskmaster;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import com.taskmaster.config.JpaConfig;
import com.taskmaster.domain.Project;
import com.taskmaster.domain.Task;
import com.taskmaster.domain.User;
import com.taskmaster.enums.TaskStatus;
import com.taskmaster.repository.ProjectRepository;
import com.taskmaster.repository.TaskRepository;
import com.taskmaster.repository.UserRepository;

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
class TaskRepositoryTest {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesAndFindsTasksByProjectAndAssignee() {
        User creator = userRepository.saveAndFlush(
                new User("Creator", "creator@example.com", "hash"));
        User assignee = userRepository.saveAndFlush(
                new User("Assignee", "assignee@example.com", "hash"));
        Project project = projectRepository.saveAndFlush(new Project("Taskmaster", null));
        Task task = taskRepository.saveAndFlush(new Task(
                "Implement tasks",
                "Add the task model",
                LocalDate.of(2026, 9, 15),
                project,
                creator,
                assignee));

        assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()))
                .extracting(Task::getId)
                .contains(task.getId());
        assertThat(taskRepository.findByAssignedToIdOrderByDueDateAsc(assignee.getId()))
                .extracting(Task::getId)
                .contains(task.getId());
    }

    @Test
    void completedAtIsSetAndClearedWithStatusLifecycle() {
        User creator = userRepository.saveAndFlush(
                new User("Creator", "lifecycle@example.com", "hash"));
        Project project = projectRepository.saveAndFlush(new Project("Lifecycle", null));
        Task task = taskRepository.saveAndFlush(
                new Task("Lifecycle task", null, null, project, creator, null));

        assertThat(task.getCompletedAt()).isNull();

        task.setStatus(TaskStatus.COMPLETED);
        taskRepository.saveAndFlush(task);
        assertThat(task.getCompletedAt()).isNotNull();

        task.setStatus(TaskStatus.IN_PROGRESS);
        taskRepository.saveAndFlush(task);
        assertThat(task.getCompletedAt()).isNull();
    }
}
