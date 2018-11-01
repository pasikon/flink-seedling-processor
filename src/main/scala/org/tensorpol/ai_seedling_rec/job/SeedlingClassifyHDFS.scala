package org.tensorpol.ai_seedling_rec.job

import java.util.Properties

import com.tensorpol.common.domain.{Msg, Request, Response}
import com.tensorpol.seedlingsinterface.domain.{SeedlingClassifyRequest, SeedlingClassifyResponse}
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer011, FlinkKafkaProducer011}
import org.tensorpol.ai_seedling_rec.classifier.ImageClassifierCoProcessFunction
import org.tensorpol.ai_seedling_rec.imageprocessing.helper.ImgResizeInput
import org.tensorpol.ai_seedling_rec.imageprocessing.{ImageResizeMapFunction, SeedlingPicture, TFModel}
import org.tensorpol.ai_seedling_rec.serialization.{ByteArrSchema, TensorpolMsgRequestSchema, TensorpolMsgResponseSchema}


object SeedlingClassifyHDFS {

  type CorrelationId = String

  val seedlingArr: Array[String] = "Black-grass\nCharlock\nCleavers\nCommon Chickweed\nCommon wheat\nFat Hen\nLoose Silky-bent\nMaize\nScentless Mayweed\nShepherds Purse\nSmall-flowered Cranesbill\nSugar beet"
    .split("\n")

  def main(args: Array[String]) {

    val env = StreamExecutionEnvironment.getExecutionEnvironment

    val properties = new Properties()
    //TODO: externalize config
    properties.setProperty("bootstrap.servers", /*"kafka011:9092"*/ "localhost:9092")
    properties.setProperty("group.id", "test")
//    properties.setProperty("auto_offset_reset", "latest")
    val inputImagesConsumer = new FlinkKafkaConsumer011[Msg[Request]]("seedlings_requests", new TensorpolMsgRequestSchema, properties)
    val modelConsumer = new FlinkKafkaConsumer011[Array[Byte]]("tfmodels", new ByteArrSchema, properties)

    inputImagesConsumer.setStartFromEarliest()
    modelConsumer.setStartFromLatest()

    val inputRequests: DataStream[Msg[Request]] = env.addSource(inputImagesConsumer)
    val modelStream: DataStream[Array[Byte]] = env.addSource(modelConsumer)

    val models: DataStream[TFModel] = modelStream.map(TFModel("tfmodel", _))

    val hdfsPicResizedNormalized: DataStream[Tuple2[CorrelationId, SeedlingPicture]] = inputRequests
      .map(msgr => msgr match {case SeedlingClassifyRequest.matcher(r) => r})
      .map(seedlingClassifyRequest => ImgResizeInput(300, 300, seedlingClassifyRequest))
      .map(new ImageResizeMapFunction)
      .map(resized_req => resized_req.id -> SeedlingPicture(resized_req.payload.image.img_bytes, 255))

    val classificationSoftmaxMaxArgIdx: DataStream[Msg[Response]] = models
      .connect(hdfsPicResizedNormalized)
      .process(new ImageClassifierCoProcessFunction)
      .map(corr_softmax => Msg(SeedlingClassifyResponse(seedlingArr(corr_softmax._2.indexOf(corr_softmax._2.max))), corr_softmax._1))

    //TODO: externalize config
    classificationSoftmaxMaxArgIdx.addSink(
      new FlinkKafkaProducer011[Msg[Response]](/*"kafka011:9092"*/"localhost:9092", "seedlings_responses", new TensorpolMsgResponseSchema)
    )

    env.execute()
  }

}
