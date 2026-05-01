package com.devtrack.service;

import com.devtrack.dto.request.CommentRequest;
import com.devtrack.dto.response.CommentResponse;
import com.devtrack.entity.Comment;
import com.devtrack.entity.Issue;
import com.devtrack.entity.User;
import com.devtrack.exception.ResourceNotFoundException;
import com.devtrack.exception.UnauthorizedException;
import com.devtrack.repository.CommentRepository;
import com.devtrack.repository.IssueRepository;
import com.devtrack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;

    @Transactional
    public CommentResponse addComment(Long issueId, CommentRequest request, String email) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue", issueId));
        User author = getUserByEmail(email);

        Comment comment = Comment.builder()
                .content(request.getContent())
                .issue(issue)
                .author(author)
                .build();

        Comment saved = commentRepository.save(comment);
        return mapToResponse(saved);
    }

    public Page<CommentResponse> getComments(Long issueId, Pageable pageable) {
        if (!issueRepository.existsById(issueId)) {
            throw new ResourceNotFoundException("Issue", issueId);
        }

        return commentRepository.findByIssueId(issueId, pageable).map(this::mapToResponse);
    }

    @Transactional
    public void deleteComment(Long issueId, Long commentId, String email) {
        if (!issueRepository.existsById(issueId)) {
            throw new ResourceNotFoundException("Issue", issueId);
        }

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));

        if (!comment.getIssue().getId().equals(issueId)) {
            throw new ResourceNotFoundException("Comment with id " + commentId + " not found in issue " + issueId);
        }

        User user = getUserByEmail(email);

        // Users can only delete their own comments
        if (!comment.getAuthor().getId().equals(user.getId())) {
            throw new UnauthorizedException("You can only delete your own comments");
        }

        commentRepository.delete(comment);
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    private CommentResponse mapToResponse(Comment comment) {
        return CommentResponse.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .authorId(comment.getAuthor().getId())
                .authorName(comment.getAuthor().getName())
                .issueId(comment.getIssue().getId())
                .build();
    }
}
