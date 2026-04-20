package com.example.app.service;

import com.example.app.dto.CommentRequest;
import com.example.app.entity.Comment;
import com.example.app.entity.Post;
import com.example.app.repo.CommentRepository;
import com.example.app.repo.PostRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor    
public class CommentService{
    
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final ViralityService viralityService;
    private final GuardrailService guardrailService;

    public Comment addComment(Long postId, CommentRequest commentRequest) {
        boolean isBot = commentRequest.isBot();
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        Comment parentComment = null;
        int depthLevel = 1;
        if (commentRequest.getParent_comment_id() != null) {
            parentComment = commentRepository.findById(commentRequest.getParent_comment_id())
                    .orElseThrow(() -> new RuntimeException("Parent comment not found"));
            if (!parentComment.getPost_id().equals(postId)) {
                throw new RuntimeException("Parent comment does not belong to this post");
            }
            depthLevel = parentComment.getDepth_level() + 1;
        }

        if (isBot) {
            guardrailService.verticalCap(depthLevel);
        }

        String cooldownKey = null;
        boolean horizontalIncremented = false;

        try {
            if (isBot) {
                Long humanId = parentComment != null
                        ? parentComment.getAuthor_id()
                        : post.getAuthor_id();

                cooldownKey = guardrailService.cooldownCap(commentRequest.getAuthor_id(), humanId);
                guardrailService.horizontalCap(postId);
                horizontalIncremented = true;
            }

        Comment comment = new Comment();
        comment.setPost_id(postId);
        comment.setAuthor_id(commentRequest.getAuthor_id());
        comment.setContent(commentRequest.getContent());
        comment.setParent_comment_id(commentRequest.getParent_comment_id());
        comment.setDepth_level(depthLevel);
        comment.setCreated_at(LocalDateTime.now());
            Comment savedComment = commentRepository.save(comment);
            System.out.println("Comment created with ID: " + savedComment.getId());
            String type = isBot ? "BOT_REPLY" : "HUMAN_COMMENT";
            Long newScore = viralityService.increaseVirality(postId, type);

            System.out.println("Post " + postId + " virality after comment: " + newScore);

            return savedComment;
        } catch (RuntimeException ex) {
            if (horizontalIncremented) {
                guardrailService.revertHorizontalCap(postId);
            }
            if (cooldownKey != null) {
                guardrailService.revertCooldownCap(cooldownKey);
            }
            throw ex;
        }
    }
}
