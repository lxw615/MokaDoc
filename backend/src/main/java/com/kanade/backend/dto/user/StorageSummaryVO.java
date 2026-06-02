package com.kanade.backend.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Current user's document storage summary.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageSummaryVO implements Serializable {

    private Long usedBytes;

    private Long totalBytes;

    private Integer usagePercent;

    private Integer documentCount;

    private Long averageBytes;
}
