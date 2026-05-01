package com.devtrack.dto.response;

import com.devtrack.enums.IssuePriority;
import com.devtrack.enums.IssueStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssueResponse {

    private Long id;
    private String title;
    private String description;
    private IssueStatus status;
    private IssuePriority priority;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    private Long projectId;
    private String projectName;
    private Long assigneeId;
    private String assigneeName;
}
