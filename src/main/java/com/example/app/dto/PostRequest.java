package com.example.app.dto;

import lombok.Data;

@Data
public class PostRequest {
    private String content;
    private Long author_id;
    private boolean isBot;
}
