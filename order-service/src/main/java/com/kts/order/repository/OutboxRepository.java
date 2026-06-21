package com.kts.order.repository;

import com.kts.order.domain.OutboxEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /** Партія неопублікованих подій для relay (FIFO за id). */
    List<OutboxEvent> findTop200ByPublishedFalseOrderByIdAsc();

    /** Скільки подій ще чекають публікації — довговічний буфер (метрика 3.4.2). */
    long countByPublishedFalse();
}
