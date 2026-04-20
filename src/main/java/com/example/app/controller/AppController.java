package com.example.app.controller;

import com.example.app.dto.CommentRequest;
import com.example.app.dto.PostRequest;
import com.example.app.entity.Comment;
import com.example.app.entity.Post;
import com.example.app.service.CommentService;
import com.example.app.service.PostService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AppController {

    private final PostService postService;
    private final CommentService commentService;

    @PostMapping("/posts")
    public ResponseEntity<Map<String, Object>> createPost(@RequestBody PostRequest postRequest) {
        Post savedPost = postService.createPost(postRequest);
        Map<String, Object> response = new HashMap<>();
        response.put("id", savedPost.getId());
        response.put("content", savedPost.getContent());
        response.put("author_id", savedPost.getAuthor_id());
        response.put("created_at", savedPost.getCreated_at());
        response.put("virality_score", 0L);
        response.put("message", "Post created successfully");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<Map<String, Object>> addComment(@PathVariable Long postId, @RequestBody CommentRequest commentRequest) {
    
        Comment savedComment = commentService.addComment(postId, commentRequest);
        Long virality = postService.getPostVirality(postId);
        Map<String, Object> response = new HashMap<>();
        response.put("comment_id", savedComment.getId());
        response.put("post_id", postId);
        response.put("content", savedComment.getContent());
        response.put("author_id", savedComment.getAuthor_id());
        response.put("depth_level", savedComment.getDepth_level());
        response.put("post_virality_after_comment", virality);
        response.put("message", "Comment added successfully");
        
        return ResponseEntity.ok(response);
    }
    

    @PostMapping("/posts/{postId}/like")
    public ResponseEntity<Map<String, Object>> likePost(@PathVariable Long postId, @RequestParam Long userId,@RequestParam boolean isBot) {
        
        Long newVirality = postService.likePost(postId, userId, isBot);
        Map<String, Object> response = new HashMap<>();
        response.put("post_id", postId);
        response.put("liked_by_user", userId);
        response.put("bot_like", isBot);
        response.put("points_added", isBot ? 1 : 20);
        response.put("new_virality_score", newVirality);
        response.put("message", "Post liked successfully");
        return ResponseEntity.ok(response);
    }
}
