package com.linkedin.openhouse.datalayout.config;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.actions.BinPackStrategy;
import org.apache.iceberg.actions.RewriteDataFiles;

@ToString
@Builder
@Getter
public final class DataCompactionConfig {
  public static final long DEFAULT_TARGET_BYTE_SIZE =
      TableProperties.WRITE_TARGET_FILE_SIZE_BYTES_DEFAULT;
  public static final double DEFAULT_MIN_BYTE_SIZE_RATIO =
      BinPackStrategy.MIN_FILE_SIZE_DEFAULT_RATIO;
  public static final double DEFAULT_MAX_BYTE_SIZE_RATIO =
      BinPackStrategy.MAX_FILE_SIZE_DEFAULT_RATIO;
  public static final int DEFAULT_MIN_INPUT_FILES = BinPackStrategy.MIN_INPUT_FILES_DEFAULT;
  public static final int DEFAULT_MAX_CONCURRENT_FILE_GROUP_REWRITES =
      RewriteDataFiles.MAX_CONCURRENT_FILE_GROUP_REWRITES_DEFAULT;
  public static final int DEFAULT_PARTIAL_PROGRESS_MAX_COMMITS =
      RewriteDataFiles.PARTIAL_PROGRESS_MAX_COMMITS_DEFAULT;
  public static final double DEFAULT_ENTROPY_THRESHOLD = 10000.0; // 100MB variance

  @Builder.Default private long targetByteSize = DEFAULT_TARGET_BYTE_SIZE;
  @Builder.Default private int minInputFiles = DEFAULT_MIN_INPUT_FILES;

  @Builder.Default
  private int maxConcurrentFileGroupRewrites = DEFAULT_MAX_CONCURRENT_FILE_GROUP_REWRITES;

  @Builder.Default private boolean partialProgressEnabled = true;
  @Builder.Default private int partialProgressMaxCommits = DEFAULT_PARTIAL_PROGRESS_MAX_COMMITS;
  @Builder.Default private double fileEntropyThreshold = DEFAULT_ENTROPY_THRESHOLD;
}