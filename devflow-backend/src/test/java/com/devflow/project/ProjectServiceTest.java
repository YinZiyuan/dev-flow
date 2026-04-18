package com.devflow.project;

import com.devflow.domain.project.*;
import com.devflow.domain.user.User;
import com.devflow.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock ProjectRepository projectRepository;
    @Mock UserRepository userRepository;
    @InjectMocks ProjectService projectService;

    @Test
    void createProject_validUser_savesAndReturns() {
        var user = new User();
        user.setEmail("test@example.com");

        var req = new ProjectService.CreateProjectRequest("My App", "desc", "Java Spring Boot", "/code/myapp");
        var saved = new Project();
        saved.setId(UUID.randomUUID());
        saved.setName("My App");
        saved.setUser(user);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(projectRepository.save(any())).thenReturn(saved);

        var result = projectService.create("test@example.com", req);

        assertThat(result.name()).isEqualTo("My App");
    }

    @Test
    void listProjects_returnsOnlyUsersProjects() {
        var user = new User();
        user.setEmail("test@example.com");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(projectRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of());

        var result = projectService.list("test@example.com");
        assertThat(result).isEmpty();
    }
}
