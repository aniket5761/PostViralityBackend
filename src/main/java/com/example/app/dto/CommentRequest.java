package com.example.app.dto;

import lombok.Data;

@Data
public class CommentRequest {
    private Long author_id;
    private String content;
    private int depth_level;
    private boolean isBot;
}
