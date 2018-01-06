package org.michal.imgproc

import java.util.Properties

import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.assigners.{SlidingEventTimeWindows, SlidingProcessingTimeWindows}
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer010, FlinkKafkaProducer010}
import org.michal.imgproc.operator.PicStreamOperator
import org.michal.schema.ByteArrSchema

object ImageResize {

  class StringCountAggregate extends AggregateFunction[String, Int, String] {
    override def add(value: String, accumulator: Int): Int = accumulator + 1

    override def createAccumulator(): Int = 0

    override def getResult(accumulator: Int): String = accumulator.toString

    override def merge(a: Int, b: Int): Int = a + b
  }

  def main(args: Array[String]) {


    val env = StreamExecutionEnvironment.getExecutionEnvironment

    val properties = new Properties()
    properties.setProperty("bootstrap.servers", "localhost:9092")
    properties.setProperty("group.id", "test")
    val kafkaConsumer = new FlinkKafkaConsumer010[String]("image_paths", new SimpleStringSchema(), properties)
    kafkaConsumer.setStartFromLatest()
    val filePaths: DataStream[String] = env.addSource(kafkaConsumer)

    val picLoadResizeStream: DataStream[Array[Byte]] = filePaths.transform(operatorName = "operator1", new PicStreamOperator)

    //how many specific paths were processed last 10s? update every 5s
    val filePathCntStr: DataStream[String] = filePaths.keyBy(s => s).
      window(SlidingProcessingTimeWindows.of(Time.seconds(10), Time.seconds(5))).
      aggregate(new StringCountAggregate)
//    (path => {
//      println(path)
//      s"Picture processed $path"
//      })

    picLoadResizeStream.addSink(new FlinkKafkaProducer010[Array[Byte]]("localhost:9092", "images_resized", new ByteArrSchema()))
    filePathCntStr.addSink(new FlinkKafkaProducer010[String]("localhost:9092", "notification", new SimpleStringSchema()))

    env.execute()
  }

}
