package com.example.app.repo;

import com.example.app.entity.Bot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BotRepository extends JpaRepository<Bot,Long> {

}
