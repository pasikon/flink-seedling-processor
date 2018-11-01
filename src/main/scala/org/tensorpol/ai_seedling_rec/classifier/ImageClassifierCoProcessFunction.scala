package org.tensorpol.ai_seedling_rec.classifier

import java.nio.{ByteBuffer, FloatBuffer}

import org.apache.flink.runtime.state.{FunctionInitializationContext, FunctionSnapshotContext}
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction
import org.apache.flink.streaming.api.functions.co.CoProcessFunction
import org.apache.flink.util.Collector
import org.slf4j.{Logger, LoggerFactory}
import org.tensorflow.{Graph, Session, Tensor}
import org.tensorpol.ai_seedling_rec.imageprocessing.{SeedlingPicture, TFModel}
import org.tensorpol.ai_seedling_rec.model_reader.TensorFlowGraphProvider

/**
  * Joins stream of models with stream of pictures
  */
class ImageClassifierCoProcessFunction extends CoProcessFunction[TFModel, Tuple2[String, SeedlingPicture], Tuple2[String, List[Float]]]
  with CheckpointedFunction {


  val log: Logger = LoggerFactory.getLogger(classOf[ImageClassifierCoProcessFunction])

  var currentModel : Graph = _

  override def processElement1(mo: TFModel, ctx: CoProcessFunction[TFModel, Tuple2[String, SeedlingPicture], Tuple2[String, List[Float]]]#Context, out: Collector[Tuple2[String, List[Float]]]): Unit = {
    println("Updating model...")
    currentModel = TensorFlowGraphProvider.graphFromBytes(mo.model)
  }

  override def processElement2(pic: Tuple2[String, SeedlingPicture], ctx: CoProcessFunction[TFModel, Tuple2[String, SeedlingPicture], Tuple2[String, List[Float]]]#Context, out: Collector[Tuple2[String, List[Float]]]): Unit = {
    val sess = new Session(currentModel)

    val input = Tensor.create(Array(1l,300L, 300L, 3L), ByteBuffer.wrap(pic._2.bytes).asFloatBuffer())
    val learningPhase = Tensor.create(false)
    println("Input tensor shape: " + input.shape.toList.foldLeft("")((a, b) => a + s" $b"))
    val result: Tensor[_] = sess.runner.feed("input_1", input)
      .feed("keras_learning_phase", learningPhase)
      .fetch("dense_1/Softmax")
      .run().get(0)
    val floatBuffer = FloatBuffer.allocate(12)
    result.writeTo(floatBuffer)
    val list: List[Float] = floatBuffer.array().toList
    out.collect(pic._1 -> list)
    log.info(s"Correlation ID: ${pic._1} : Image classified, Softmax ouput: $list")
  }

  override def snapshotState(context: FunctionSnapshotContext): Unit = {
    //TODO: implement snapshoting
    log.info("Snapshot state...")
  }

  override def initializeState(context: FunctionInitializationContext): Unit = {

    //TODO: read from HDFS or local filesystem should go to Config
    currentModel = TensorFlowGraphProvider.fromLocalFilesystem()

    log.info("Initialization, model loading complete...")
  }


}
