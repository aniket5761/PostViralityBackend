package com.example.app.service;

import com.example.app.dto.PostRequest;
import com.example.app.entity.Post;
import com.example.app.repo.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PostService {
    
    private final PostRepository postRepository;    
    private final ViralityService viralityService;
    
  
    public Post createPost(PostRequest postRequest) {
        Post post = new Post();
        post.setAuthor_id(postRequest.getAuthor_id());
        post.setContent(postRequest.getContent());
        post.setCreated_at(LocalDateTime.now());
        Post savedPost = postRepository.save(post);
        System.out.println("Post created with ID: " + savedPost.getId());
        viralityService.getViraityScore(savedPost.getId());
        
        return savedPost;
    }
    
    public Long likePost(Long postId, Long userId, boolean isBot) {
    String type;
    if(isBot) type = "BOT_REPLY";
    else type = "HUMAN_LIKE";
    return viralityService.increaseVirality(postId, type);
}

    public Long getPostVirality(Long postId) {
        return viralityService.getViraityScore(postId);
    }
}
