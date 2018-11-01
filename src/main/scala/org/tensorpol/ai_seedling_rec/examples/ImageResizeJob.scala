package org.tensorpol.ai_seedling_rec.examples

import java.nio.file.{Files, Path, Paths}
import java.nio.{ByteBuffer, FloatBuffer}
import java.util.Properties

import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.runtime.state.{FunctionInitializationContext, FunctionSnapshotContext}
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction
import org.apache.flink.streaming.api.functions.co.CoProcessFunction
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.assigners.SlidingProcessingTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer011, FlinkKafkaProducer011}
import org.apache.flink.util.Collector
import org.tensorflow.{Graph, Session, Tensor}
import org.tensorpol.ai_seedling_rec.examples.operator.PicStreamOperator
import org.tensorpol.ai_seedling_rec.imageprocessing.{SeedlingPicture, TFModel}
import org.tensorpol.ai_seedling_rec.serialization.ByteArrSchema


//class ImageSeedlingClassifyFunction extends RichMapFunction[Array[Byte], List[Float]] {
//  private var seedlingTfModel: ValueState[Array[Byte]] = _
//
//  override def map(value: Array[Byte]): List[Float] = ???
//
//  override def open(parameters: Configuration): Unit = {
//    seedlingTfModel = getRuntimeContext.getState(
//      new ValueStateDescriptor[Array[Byte]]("seedlTfModel", createTypeInformation[Array[Byte]])
//    )
//  }
//}


class ImageClassifyProcessor extends CoProcessFunction[TFModel, SeedlingPicture, List[Float]]
  with CheckpointedFunction {

  var currentModel : Graph = _

  override def processElement1(mo: TFModel, ctx: CoProcessFunction[TFModel, SeedlingPicture, List[Float]]#Context, out: Collector[List[Float]]): Unit = {
    println("Updating model...")
    currentModel = graphFromBytes(mo.model)
  }

  override def processElement2(pic: SeedlingPicture, ctx: CoProcessFunction[TFModel, SeedlingPicture, List[Float]]#Context, out: Collector[List[Float]]): Unit = {
    val sess = new Session(currentModel)

    val input = Tensor.create(Array(1l,300L, 300L, 3L), ByteBuffer.wrap(pic.bytes).asFloatBuffer())
    val learningPhase = Tensor.create(false)
    println("Input tensor shape: " + input.shape.toList.foldLeft("")((a, b) => a + s" $b"))
    val result: Tensor[_] = sess.runner.feed("input_1", input)
      .feed("keras_learning_phase", learningPhase)
      .fetch("dense_1/Softmax")
      .run().get(0)
    val floatBuffer = FloatBuffer.allocate(12)
    result.writeTo(floatBuffer)
    val list: List[Float] = floatBuffer.array().toList
    out.collect(list)
    println(s"Image classified: $list")
  }

  override def snapshotState(context: FunctionSnapshotContext): Unit = {
    println("Snapshot state...")
  }

  override def initializeState(context: FunctionInitializationContext): Unit = {
    val path = "data/frozen_SeedlingsRecognize.pb"
    println(s"Loading saved model from $path")
    val lg: Graph = readGraph(Paths.get (path))
    currentModel = lg
    println("Model Loading complete")
  }

  private def readGraph(path: Path): Graph = {
    val graphData: Array[Byte] = Files.readAllBytes(path)
    graphFromBytes(graphData)
  }

  private def graphFromBytes(graphData: Array[Byte]): Graph = {
    val g = new Graph
    g.importGraphDef(graphData)
    g
  }
}


object ImageResize {


  class StringCountAggregate extends AggregateFunction[String, (String, Int), String] {
    override def add(value: String, accumulator: (String, Int)): (String, Int) =
      value -> (accumulator._2 + 1)

    override def createAccumulator(): (String, Int) = "" -> 0

    override def getResult(accumulator: (String, Int)): String =
      s"Path ${accumulator._1} processed ${accumulator._2} times last 10 sec..."

    override def merge(a: (String, Int), b: (String, Int)): (String, Int) = a._1 -> (a._2 + b._2)
  }

  def main(args: Array[String]) {

    val env = StreamExecutionEnvironment.getExecutionEnvironment

    val properties = new Properties()
    properties.setProperty("bootstrap.servers", "kafka:9092")
    properties.setProperty("group.id", "test")

    val imagePathsConsumer = new FlinkKafkaConsumer011[String]("image_paths", new SimpleStringSchema(), properties)
    val modelConsumer = new FlinkKafkaConsumer011[Array[Byte]]("tfmodels", new ByteArrSchema, properties)

    imagePathsConsumer.setStartFromLatest()
    modelConsumer.setStartFromLatest()

    val filePaths: DataStream[String] = env.addSource(imagePathsConsumer)
    val modelStream = env.addSource(modelConsumer)

    val models: DataStream[TFModel] = modelStream.map(TFModel("tfmodel", _))

    val picLoadResizeStream: DataStream[Array[Byte]] = filePaths.transform(operatorName = "operator1", new PicStreamOperator)
    val seedlPicStream: DataStream[SeedlingPicture] = picLoadResizeStream.map(SeedlingPicture(_, 255))

    models.connect(seedlPicStream).process(new ImageClassifyProcessor)

    //how many specific paths were processed last 10s? update every 5s
    val filePathCntStr: DataStream[String] = filePaths.keyBy(s => s).
      window(SlidingProcessingTimeWindows.of(Time.seconds(10), Time.seconds(5))).
      aggregate(new StringCountAggregate)

    picLoadResizeStream.addSink(new FlinkKafkaProducer011[Array[Byte]]("kafka:9092", "images_resized", new ByteArrSchema()))
    filePathCntStr.addSink(new FlinkKafkaProducer011[String]("kafka:9092", "notification", new SimpleStringSchema()))

    env.execute()
  }

}
