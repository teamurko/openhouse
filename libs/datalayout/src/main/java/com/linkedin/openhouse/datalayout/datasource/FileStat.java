package com.linkedin.openhouse.datalayout.datasource;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Represents the statistics of a file. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public final class FileStat implements Serializable {
  private String path;
  private long size;
}
