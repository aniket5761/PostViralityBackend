package com.example.app.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name ="comments")
@Data
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long post_id;
    private Long author_id;
    private String content;
    private Long parent_comment_id;
    private int depth_level;
    private LocalDateTime created_at;
}
