package com.devtrack.service;

import com.devtrack.dto.request.IssueRequest;
import com.devtrack.dto.response.IssueResponse;
import com.devtrack.entity.Issue;
import com.devtrack.entity.Project;
import com.devtrack.entity.User;
import com.devtrack.enums.IssuePriority;
import com.devtrack.enums.IssueStatus;
import com.devtrack.enums.Role;
import com.devtrack.exception.ResourceNotFoundException;
import com.devtrack.exception.UnauthorizedException;
import com.devtrack.repository.IssueRepository;
import com.devtrack.repository.ProjectRepository;
import com.devtrack.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IssueService {

    private final IssueRepository issueRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    @Transactional
    public IssueResponse createIssue(Long projectId, IssueRequest request, String email) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        User creator = getUserByEmail(email);

        Issue issue = Issue.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .status(IssueStatus.OPEN)
                .priority(request.getPriority())
                .project(project)
                .build();

        // Only MANAGER can assign issues
        if (request.getAssigneeId() != null) {
            if (creator.getRole() != Role.MANAGER) {
                throw new UnauthorizedException("Only MANAGER users can assign issues");
            }
            User assignee = userRepository.findById(request.getAssigneeId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", request.getAssigneeId()));
            issue.setAssignee(assignee);
        }

        Issue saved = issueRepository.save(issue);
        return mapToResponse(saved);
    }

    public Page<IssueResponse> getIssues(Long projectId, IssueStatus status, IssuePriority priority,
                                          Long assigneeId, Pageable pageable) {
        // Verify project exists
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project", projectId);
        }

        Specification<Issue> spec = buildSpecification(projectId, status, priority, assigneeId);
        return issueRepository.findAll(spec, pageable).map(this::mapToResponse);
    }

    public IssueResponse getIssueById(Long projectId, Long issueId) {
        Issue issue = findIssueByProjectAndId(projectId, issueId);
        return mapToResponse(issue);
    }

    @Transactional
    public IssueResponse updateIssue(Long projectId, Long issueId, IssueRequest request, String email) {
        Issue issue = findIssueByProjectAndId(projectId, issueId);
        User user = getUserByEmail(email);

        issue.setTitle(request.getTitle());
        issue.setDescription(request.getDescription());
        issue.setPriority(request.getPriority());

        // Only MANAGER can reassign
        if (request.getAssigneeId() != null) {
            if (user.getRole() != Role.MANAGER) {
                throw new UnauthorizedException("Only MANAGER users can assign issues");
            }
            User assignee = userRepository.findById(request.getAssigneeId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", request.getAssigneeId()));
            issue.setAssignee(assignee);
        }

        Issue updated = issueRepository.save(issue);
        return mapToResponse(updated);
    }

    @Transactional
    public IssueResponse transitionStatus(Long projectId, Long issueId, IssueStatus newStatus, String email) {
        Issue issue = findIssueByProjectAndId(projectId, issueId);
        User user = getUserByEmail(email);

        // DEVELOPER can only transition their own issues
        if (user.getRole() == Role.DEVELOPER) {
            if (issue.getAssignee() == null || !issue.getAssignee().getId().equals(user.getId())) {
                throw new UnauthorizedException("DEVELOPER can only transition status of their own assigned issues");
            }
        }

        // Validate status transition
        validateStatusTransition(issue.getStatus(), newStatus);

        issue.setStatus(newStatus);
        Issue updated = issueRepository.save(issue);
        return mapToResponse(updated);
    }

    @Transactional
    public void deleteIssue(Long projectId, Long issueId, String email) {
        Issue issue = findIssueByProjectAndId(projectId, issueId);
        User user = getUserByEmail(email);

        if (user.getRole() != Role.MANAGER) {
            throw new UnauthorizedException("Only MANAGER users can delete issues");
        }

        issueRepository.delete(issue);
    }

    // ---- Helper Methods ----

    private Issue findIssueByProjectAndId(Long projectId, Long issueId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project", projectId);
        }

        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue", issueId));

        if (!issue.getProject().getId().equals(projectId)) {
            throw new ResourceNotFoundException("Issue with id " + issueId + " not found in project " + projectId);
        }

        return issue;
    }

    private void validateStatusTransition(IssueStatus currentStatus, IssueStatus newStatus) {
        // Valid transitions: OPEN → IN_PROGRESS → IN_REVIEW → RESOLVED → CLOSED
        boolean valid = switch (currentStatus) {
            case OPEN -> newStatus == IssueStatus.IN_PROGRESS;
            case IN_PROGRESS -> newStatus == IssueStatus.IN_REVIEW;
            case IN_REVIEW -> newStatus == IssueStatus.RESOLVED;
            case RESOLVED -> newStatus == IssueStatus.CLOSED;
            case CLOSED -> false;
        };

        if (!valid) {
            throw new IllegalArgumentException(
                    String.format("Invalid status transition from %s to %s", currentStatus, newStatus));
        }
    }

    private Specification<Issue> buildSpecification(Long projectId, IssueStatus status,
                                                     IssuePriority priority, Long assigneeId) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(criteriaBuilder.equal(root.get("project").get("id"), projectId));

            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (priority != null) {
                predicates.add(criteriaBuilder.equal(root.get("priority"), priority));
            }
            if (assigneeId != null) {
                predicates.add(criteriaBuilder.equal(root.get("assignee").get("id"), assigneeId));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    private IssueResponse mapToResponse(Issue issue) {
        return IssueResponse.builder()
                .id(issue.getId())
                .title(issue.getTitle())
                .description(issue.getDescription())
                .status(issue.getStatus())
                .priority(issue.getPriority())
                .createdAt(issue.getCreatedAt())
                .updatedAt(issue.getUpdatedAt())
                .projectId(issue.getProject().getId())
                .projectName(issue.getProject().getName())
                .assigneeId(issue.getAssignee() != null ? issue.getAssignee().getId() : null)
                .assigneeName(issue.getAssignee() != null ? issue.getAssignee().getName() : null)
                .build();
    }
}
