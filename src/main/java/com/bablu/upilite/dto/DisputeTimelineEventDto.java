package com.bablu.upilite.dto;

import com.bablu.upilite.entity.DisputeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeTimelineEventDto {
    private DisputeStatus status;
    private String title;
    private String description;
    private LocalDateTime occurredAt;
    private boolean completed;
}
