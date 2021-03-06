package table;

import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableConfig;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

import java.time.Duration;

import static org.apache.flink.table.api.Expressions.$;

public class TimestampWindowDemo {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        tableEnv.executeSql("create table input (" +
                "username STRING, " +
                "url STRING, " +
                "ts BIGINT," +
                "et as TO_TIMESTAMP( FROM_UNIXTIME(ts/1000)), " +
                "WATERMARK FOR et as et - INTERVAL '1' SECOND" +
                ") with(" +
                "'connector'='filesystem', " +
                "'path'= 'input/sample.txt', " +
                "'format' = 'csv' " +
                ")");

        SingleOutputStreamOperator<TableApiDemo.Event> stream = env.fromElements(
                new TableApiDemo.Event("czl", 1),
                new TableApiDemo.Event("czl", 2)
        ).assignTimestampsAndWatermarks(WatermarkStrategy.<TableApiDemo.Event>forBoundedOutOfOrderness(Duration.ZERO)
                .withTimestampAssigner(new SerializableTimestampAssigner<TableApiDemo.Event>() {
                    @Override
                    public long extractTimestamp(TableApiDemo.Event e, long l) {
                        return e.ts;
                    }
                }));
        // “分组窗口”函数
        Table table = tableEnv.fromDataStream(stream, $("username"), $("ts"), $("et").rowtime());
        Table result = tableEnv.sqlQuery("select " +
                "username, count(1) as cnt, TUMBLE_END(et, INTERVAL '1' SECOND) as endT " +
                "from input " +
                "group by username, TUMBLE(et, INTERVAL '1' SECOND)");
        tableEnv.toDataStream(result).print();

        //窗口表值函数（ Windowing TVFs，新版本1.13）
        Table result2 = tableEnv.sqlQuery("select " +
                "username, count(1) as cnt, window_end as endT " +
                "from TABLE(TUMBLE(TABLE input, DESCRIPTOR(et), INTERVAL '1' second)) " +
                "group by username, window_start, window_end");
        tableEnv.toDataStream(result2).print("2");

        // 聚合
        Table result3 = tableEnv.sqlQuery("select " +
                "username, avg(ts) over(partition by username order by et rows between 3 preceding and current row) as avg_ts " +
                "from input");
        tableEnv.toDataStream(result3).print("3");

        env.execute();

//        TableConfig config = tableEnv.getConfig();
//        config.setIdleStateRetention(Duration.ofSeconds(60));
//
//        Configuration configuration = tableEnv.getConfig().getConfiguration();
//        configuration.setString("table.exec.state.ttl", "60 min");
    }
}
