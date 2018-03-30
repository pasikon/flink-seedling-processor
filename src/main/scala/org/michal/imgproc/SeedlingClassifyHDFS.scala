package org.michal.imgproc

import java.nio.file.{Files, Path}
import java.nio.{ByteBuffer, FloatBuffer}
import java.util.Properties

import org.apache.flink.api.common.functions.MapFunction
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.core.fs
import org.apache.flink.runtime.fs.hdfs.{HadoopDataInputStream, HadoopFileSystem}
import org.apache.flink.runtime.state.{FunctionInitializationContext, FunctionSnapshotContext}
import org.apache.flink.shaded.guava18.com.google.common.io.ByteStreams
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction
import org.apache.flink.streaming.api.functions.co.CoProcessFunction
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer011, FlinkKafkaProducer011}
import org.apache.flink.util.Collector
import org.bytedeco.javacpp.opencv_core.Size
import org.bytedeco.javacpp.{BytePointer, opencv_imgcodecs, opencv_imgproc}
import org.michal.imgproc.hdfsutils.HdfsUtils
import org.michal.imgproc.model.{SeedlingPicture, TFModel}
import org.michal.schema.ByteArrSchema
import org.tensorflow.{Graph, Session, Tensor}


class ImageClassifyProcessorHDFS extends CoProcessFunction[TFModel, SeedlingPicture, List[Float]]
  with CheckpointedFunction {

  var currentModel : Graph = _
  //  var newModel : Option[TFModel] = None


  //  override def open(parameters: Configuration): Unit = {
  //    val modelDesc = new ValueStateDescriptor[]()
  //  }

  override def processElement1(mo: TFModel, ctx: CoProcessFunction[TFModel, SeedlingPicture, List[Float]]#Context, out: Collector[List[Float]]): Unit = {
    println("Updating model...")
    currentModel = graphFromBytes(mo.model)
  }

  override def processElement2(pic: SeedlingPicture, ctx: CoProcessFunction[TFModel, SeedlingPicture, List[Float]]#Context, out: Collector[List[Float]]): Unit = {
    val sess = new Session(currentModel)

    val input = Tensor.create(Array(1l,300L, 300L, 3L), ByteBuffer.wrap(pic.bytes).asFloatBuffer())
    val learningPhase = Tensor.create(false)
    println(s"Input shape: ${input.shape().toString}")
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
    val flinkHadoopFileSystem: HadoopFileSystem = HdfsUtils.getFlinkHadoopFileSystem

    val modelDir = "tf_models/"
    val modelFile = "frozen_SeedlingsRecognize.pb"
    val hadoopDataInputStream: HadoopDataInputStream = flinkHadoopFileSystem.open(new fs.Path(modelDir, modelFile))

    val modelBytes: Array[Byte] = ByteStreams.toByteArray(hadoopDataInputStream.getHadoopInputStream.getWrappedStream)
    val lg = graphFromBytes(modelBytes)
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

class ReadImgByteArrayHDFSFunction extends MapFunction[String, Array[Byte]] {
  override def map(filename: String): Array[Byte] = {
    val fhdfs = HdfsUtils.getFlinkHadoopFileSystem

    val hadoopDataInputStream = fhdfs.open(new fs.Path("seedling_pics/", filename))
    //      val img: BufferedImage = ImageIO.read(hadoopDataInputStream.getHadoopInputStream.getWrappedStream)

    import org.bytedeco.javacpp.opencv_core.Mat
    val bytes: Array[Byte] = ByteStreams.toByteArray(hadoopDataInputStream.getHadoopInputStream.getWrappedStream)
    println(s"Read ${bytes.length} bytes for $filename from HDFS...")
    val mat = new Mat(new BytePointer(ByteBuffer.wrap(bytes)))
    val matSrc = opencv_imgcodecs.imdecode(mat, opencv_imgcodecs.CV_LOAD_IMAGE_UNCHANGED)

    val matDst = new Mat()
    opencv_imgproc.resize(matSrc, matDst, new Size(300, 300))

    val darr: Array[Byte] = new Array[Byte]((matDst.total() * matDst.channels()).toInt)
    matDst.data().get(darr)
    darr
  }
}


object SeedlingClassifyHDFS {

  def main(args: Array[String]) {

    val env = StreamExecutionEnvironment.getExecutionEnvironment

    val properties = new Properties()
    properties.setProperty("bootstrap.servers", "kafka:9092")
    properties.setProperty("group.id", "test")
    val imagePathsConsumerHDFS = new FlinkKafkaConsumer011[String]("img_file_hdfs", new SimpleStringSchema(), properties)
    val modelConsumer = new FlinkKafkaConsumer011[Array[Byte]]("tfmodels", new ByteArrSchema, properties)

    imagePathsConsumerHDFS.setStartFromEarliest()
    modelConsumer.setStartFromLatest()

    val filePaths: DataStream[String] = env.addSource(imagePathsConsumerHDFS)
    val modelStream: DataStream[Array[Byte]] = env.addSource(modelConsumer)

    val models: DataStream[TFModel] = modelStream.map(TFModel("tfmodel", _))

    val hdfsPicResizedNormalized: DataStream[SeedlingPicture] = filePaths.
      map(new ReadImgByteArrayHDFSFunction).
      map(SeedlingPicture(_, 255))

    val classificationSoftmaxMaxArgIdx: DataStream[String] = models.
      connect(hdfsPicResizedNormalized).
      process(new ImageClassifyProcessorHDFS).
      map(softmax => softmax.indexOf(softmax.max).toString)

    classificationSoftmaxMaxArgIdx.addSink(
      new FlinkKafkaProducer011[String]("kafka:9092", "img_class_softmax", new SimpleStringSchema())
    )

    env.execute()
  }

}
