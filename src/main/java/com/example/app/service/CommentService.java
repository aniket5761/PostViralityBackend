package com.example.app.service;

import com.example.app.dto.CommentRequest;
import com.example.app.entity.Comment;
import com.example.app.repo.CommentRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor    
public class CommentService{
    
    private final CommentRepository commentRepository;
    private final ViraityService viraityService;

    public Comment addComment(Long postId, CommentRequest commentRequest) {
        Comment comment = new Comment();
        comment.setPost_id(postId);
        comment.setAuthor_id(commentRequest.getAuthor_id());
        comment.setContent(commentRequest.getContent());
        comment.setDepth_level(commentRequest.getDepth_level());
        comment.setCreated_at(LocalDateTime.now());
        
        Comment savedComment = commentRepository.save(comment);
        System.out.println("Comment created with ID: " + savedComment.getId());
    
        String type;
        if(commentRequest.isBot()){
            type = "BOT_REPLY";
        }
        else type = "HUMAN_COMMENT";
        Long newScore = viraityService.increaseVirality(postId, type
        );
        
        System.out.println("Post " + postId + " virality after comment: " + newScore);
        
        return savedComment;
    }
}
