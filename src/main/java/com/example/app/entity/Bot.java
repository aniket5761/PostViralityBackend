package com.example.app.entity;

import jakarta.persistence.*;
import lombok.Data;

@Table(name ="bots")
@Entity
@Data
public class Bot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String persona_description;
}
