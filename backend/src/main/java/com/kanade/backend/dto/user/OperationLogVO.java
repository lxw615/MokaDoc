package com.kanade.backend.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * User-facing operation log item.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationLogVO implements Serializable {

    private Long id;

    private LocalDateTime time;

    private String action;

    private String source;
}
