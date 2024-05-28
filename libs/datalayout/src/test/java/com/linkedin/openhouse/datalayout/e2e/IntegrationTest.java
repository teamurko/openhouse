package com.linkedin.openhouse.datalayout.e2e;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.linkedin.openhouse.datalayout.datasource.TableFileStats;
import com.linkedin.openhouse.datalayout.layoutselection.DataLayoutOptimizationStrategy;
import com.linkedin.openhouse.datalayout.layoutselection.OpenHouseDataLayoutGenerator;
import com.linkedin.openhouse.tablestest.OpenHouseSparkITest;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class IntegrationTest extends OpenHouseSparkITest {
  @Test
  public void testLayoutSelectionWithPersistence() throws Exception {
    final String testTable = "db.test_table_selection";
    try (SparkSession spark = getSparkSession()) {
      spark.sql("USE openhouse");
      createTestTable(spark, testTable, 10);
      TableFileStats tableFileStats =
          TableFileStats.builder().tableName(testTable).spark(spark).build();
      OpenHouseDataLayoutGenerator layoutSelectionPolicy =
          OpenHouseDataLayoutGenerator.builder().tableFileStats(tableFileStats).build();
      List<DataLayoutOptimizationStrategy> compactionLayouts = layoutSelectionPolicy.generate();
      Assertions.assertEquals(526385152, compactionLayouts.get(0).getConfig().getTargetByteSize());
      Gson gson = new GsonBuilder().create();
      Type type = new TypeToken<ArrayList<DataLayoutOptimizationStrategy>>() {}.getType();
      String serializedLayout = gson.toJson(compactionLayouts, type);
      spark.sql(
          String.format(
              "alter table %s set tblproperties ('data-layout' = '%s')",
              testTable, StringEscapeUtils.escapeJava(serializedLayout)));
      serializedLayout =
          spark
              .sql(String.format("show tblproperties %s ('data-layout')", testTable))
              .collectAsList()
              .get(0)
              .getString(1);
      compactionLayouts = gson.fromJson(StringEscapeUtils.unescapeJava(serializedLayout), type);
      Assertions.assertEquals(526385152, compactionLayouts.get(0).getConfig().getTargetByteSize());
    }
  }

  private void createTestTable(SparkSession spark, String tableName, int numRows) {
    spark.sql(String.format("create table %s (id int, data string)", tableName));
    for (int i = 0; i < numRows; ++i) {
      spark.sql(String.format("insert into %s values (%d, 'data')", tableName, i));
    }
  }
}
