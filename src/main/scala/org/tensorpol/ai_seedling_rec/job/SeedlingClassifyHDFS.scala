package org.tensorpol.ai_seedling_rec.job

import java.util.Properties

import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer011, FlinkKafkaProducer011}
import org.tensorpol.ai_seedling_rec.classifier.ImageClassifierCoProcessFunction
import org.tensorpol.ai_seedling_rec.imageprocessing.{ImageResizeMapFunction, SeedlingPicture, TFModel}
import org.tensorpol.ai_seedling_rec.imageprocessing.helper.ImgResizeInput
import org.tensorpol.ai_seedling_rec.serialization.ByteArrSchema


object SeedlingClassifyHDFS {

  val seedlingArr: Array[String] = "Black-grass\nCharlock\nCleavers\nCommon Chickweed\nCommon wheat\nFat Hen\nLoose Silky-bent\nMaize\nScentless Mayweed\nShepherds Purse\nSmall-flowered Cranesbill\nSugar beet"
    .split("\n")

  def main(args: Array[String]) {

    val env = StreamExecutionEnvironment.getExecutionEnvironment

    val properties = new Properties()
    //TODO: externalize config
    properties.setProperty("bootstrap.servers", "kafka011:9092")
    properties.setProperty("group.id", "test")
    val inputImagesConsumer = new FlinkKafkaConsumer011[Array[Byte]]("seedling_pics", new ByteArrSchema, properties)
    val modelConsumer = new FlinkKafkaConsumer011[Array[Byte]]("tfmodels", new ByteArrSchema, properties)

    inputImagesConsumer.setStartFromEarliest()
    modelConsumer.setStartFromLatest()

    val inputImages: DataStream[Array[Byte]] = env.addSource(inputImagesConsumer)
    val modelStream: DataStream[Array[Byte]] = env.addSource(modelConsumer)

    val models: DataStream[TFModel] = modelStream.map(TFModel("tfmodel", _))

    val hdfsPicResizedNormalized: DataStream[SeedlingPicture] = inputImages
      .map(imgBytes => ImgResizeInput(300, 300, imgBytes))
      .map(new ImageResizeMapFunction)
      .map(SeedlingPicture(_, 255))

    val classificationSoftmaxMaxArgIdx: DataStream[String] = models
      .connect(hdfsPicResizedNormalized)
      .process(new ImageClassifierCoProcessFunction)
      .map(softmax => seedlingArr(softmax.indexOf(softmax.max)))

    //TODO: externalize config
    classificationSoftmaxMaxArgIdx.addSink(
      new FlinkKafkaProducer011[String]("kafka011:9092", "img_class_softmax", new SimpleStringSchema())
    )

    env.execute()
  }

}
