package com.linkedin.openhouse.datalayout.layoutselection;

import com.linkedin.openhouse.datalayout.config.DataCompactionConfig;
import com.linkedin.openhouse.datalayout.datasource.FileStat;
import com.linkedin.openhouse.datalayout.datasource.TableFileStats;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import org.apache.spark.api.java.function.MapFunction;
import org.apache.spark.api.java.function.ReduceFunction;
import org.apache.spark.sql.Encoders;

/**
 * Data layout optimization strategies generator for OpenHouse. Generates a list of strategies with
 * cost.
 */
@Builder
public class OpenHouseDataLayoutGenerator implements DataLayoutGenerator {
  private static final long FILE_BLOCK_SIZE_BYTES = 1024L * 1024L * 256;
  private static final long FILE_BLOCK_MARGIN_BYTES = 1024L * 1024L * 10;
  private static final long NUM_FILE_GROUPS_PER_COMMIT = 50;
  private static final double FILE_ENTROPY_THRESHOLD = 100.0;
  private final TableFileStats tableFileStats;

  /**
   * Generate a list of data layout optimization strategies based on the table file stats and
   * historic query patterns.
   */
  @Override
  public List<DataLayoutOptimizationStrategy> generate() {
    long totalSize =
        tableFileStats
            .get()
            .map((MapFunction<FileStat, Long>) FileStat::getSize, Encoders.LONG())
            .reduce((ReduceFunction<Long>) Long::sum);
    long numFiles = tableFileStats.get().count();

    DataCompactionConfig.DataCompactionConfigBuilder configBuilder = DataCompactionConfig.builder();
    // Make sure the last block is almost full
    configBuilder.targetByteSize(2 * FILE_BLOCK_SIZE_BYTES - FILE_BLOCK_MARGIN_BYTES);

    long estimatedMaxNumFileGroups =
        totalSize / DataCompactionConfig.MAX_FILE_GROUP_SIZE_BYTES_DEFAULT;
    int maxNumCommits =
        (int) Math.min(1000L, estimatedMaxNumFileGroups / NUM_FILE_GROUPS_PER_COMMIT);
    configBuilder.partialProgressMaxCommits(maxNumCommits);
    // TODO: this should be determined by partition size in order to utilize Spark app parallelism
    configBuilder.maxConcurrentFileGroupRewrites(50);
    // don't split large files
    configBuilder.maxByteSizeRatio(10);

    return Collections.singletonList(
        DataLayoutOptimizationStrategy.builder().config(configBuilder.build()).score(1.0).build());
  }

  private double calculateCompactionCost() {
    return 0.0;
  }
}
