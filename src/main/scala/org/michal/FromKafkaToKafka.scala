package org.michal

import java.util.Properties

import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.scala._
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer011, FlinkKafkaProducer011}

object FromKafkaToKafka {

  def main(args: Array[String]): Unit = {

    val env = StreamExecutionEnvironment.getExecutionEnvironment

    val properties = new Properties()
    properties.setProperty("bootstrap.servers", "kafka:9092")
    properties.setProperty("group.id", "test")

    val stringsConsumer = new FlinkKafkaConsumer011[String]("k2k_i", new SimpleStringSchema(), properties)
    stringsConsumer.setStartFromLatest()

    val strings: DataStream[String] = env.addSource(stringsConsumer)

    strings.map(_.length.toString).addSink(new FlinkKafkaProducer011[String]("kafka:9092", "k2k_o", new SimpleStringSchema()))

    env.execute()
  }

}
