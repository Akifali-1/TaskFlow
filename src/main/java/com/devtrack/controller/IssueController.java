package com.devtrack.controller;

import com.devtrack.dto.request.IssueRequest;
import com.devtrack.dto.response.IssueResponse;
import com.devtrack.enums.IssuePriority;
import com.devtrack.enums.IssueStatus;
import com.devtrack.service.IssueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/issues")
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issueService;

    @PostMapping
    public ResponseEntity<IssueResponse> createIssue(
            @PathVariable Long projectId,
            @Valid @RequestBody IssueRequest request,
            Authentication authentication) {
        IssueResponse response = issueService.createIssue(projectId, request, authentication.getName());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<Page<IssueResponse>> getIssues(
            @PathVariable Long projectId,
            @RequestParam(required = false) IssueStatus status,
            @RequestParam(required = false) IssuePriority priority,
            @RequestParam(required = false) Long assigneeId,
            @PageableDefault(size = 10) Pageable pageable) {
        Page<IssueResponse> issues = issueService.getIssues(projectId, status, priority, assigneeId, pageable);
        return ResponseEntity.ok(issues);
    }

    @GetMapping("/{issueId}")
    public ResponseEntity<IssueResponse> getIssueById(
            @PathVariable Long projectId,
            @PathVariable Long issueId) {
        IssueResponse response = issueService.getIssueById(projectId, issueId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{issueId}")
    public ResponseEntity<IssueResponse> updateIssue(
            @PathVariable Long projectId,
            @PathVariable Long issueId,
            @Valid @RequestBody IssueRequest request,
            Authentication authentication) {
        IssueResponse response = issueService.updateIssue(projectId, issueId, request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{issueId}/status")
    public ResponseEntity<IssueResponse> transitionStatus(
            @PathVariable Long projectId,
            @PathVariable Long issueId,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        IssueStatus newStatus = IssueStatus.valueOf(body.get("status"));
        IssueResponse response = issueService.transitionStatus(projectId, issueId, newStatus, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{issueId}")
    public ResponseEntity<Void> deleteIssue(
            @PathVariable Long projectId,
            @PathVariable Long issueId,
            Authentication authentication) {
        issueService.deleteIssue(projectId, issueId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
