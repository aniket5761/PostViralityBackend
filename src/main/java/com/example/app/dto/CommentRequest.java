package com.example.app.dto;

import lombok.Data;

@Data
public class CommentRequest {
    private Long author_id;
    private String content;
    private boolean bot;
    private Long parent_comment_id;

    public void setIsBot(boolean bot) {
        this.bot = bot;
    }
}
