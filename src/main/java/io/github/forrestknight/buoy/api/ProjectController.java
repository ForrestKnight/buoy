package io.github.forrestknight.buoy.api;

import io.github.forrestknight.buoy.api.ProjectDtos.CreateProjectRequest;
import io.github.forrestknight.buoy.api.ProjectDtos.ProjectResponse;
import io.github.forrestknight.buoy.api.ProjectDtos.UpdateProjectRequest;
import io.github.forrestknight.buoy.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request,
                                                  UriComponentsBuilder uri) {
        ProjectResponse response = ProjectResponse.from(
                projectService.create(request.key(), request.name(), request.description()));
        return ResponseEntity
                .created(uri.path("/api/v1/projects/{key}").buildAndExpand(response.key()).toUri())
                .body(response);
    }

    @GetMapping
    public List<ProjectResponse> list() {
        return projectService.list().stream().map(ProjectResponse::from).toList();
    }

    @GetMapping("/{projectKey}")
    public ProjectResponse get(@PathVariable String projectKey) {
        return ProjectResponse.from(projectService.get(projectKey));
    }

    @PutMapping("/{projectKey}")
    public ProjectResponse update(@PathVariable String projectKey,
                                  @Valid @RequestBody UpdateProjectRequest request) {
        return ProjectResponse.from(
                projectService.update(projectKey, request.name(), request.description()));
    }

    @DeleteMapping("/{projectKey}")
    public ResponseEntity<Void> delete(@PathVariable String projectKey) {
        projectService.delete(projectKey);
        return ResponseEntity.noContent().build();
    }
}
