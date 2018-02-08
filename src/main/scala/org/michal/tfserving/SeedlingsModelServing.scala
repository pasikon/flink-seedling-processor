package org.michal.tfserving

import java.nio.{ByteBuffer, FloatBuffer}
import java.nio.file.{Files, Path, Paths}

import org.bytedeco.javacpp.opencv_core.{Mat, Size}
import org.bytedeco.javacpp.{opencv_imgcodecs, opencv_imgproc}

import scala.io.Source
import org.tensorflow.{Graph, Session, Tensor}

class SeedlingsModelServing(path: String) {
  import SeedlingsModelServing._

  // Constructor
  println(s"Loading saved model from $path")
  val lg: Graph = readGraph(Paths.get (path))
  val ls: Session = new Session (lg)
  println("Model Loading complete")

  def scorePic(record: Array[Byte]) = {
    val input = Tensor.create(Array(1l,300L, 300L, 3L), ByteBuffer.wrap(record).asFloatBuffer())
    val learningPhase = Tensor.create(false)
    println(s"Input shape: ${input.shape().toString}")
    val value: Tensor[_] = ls.runner.feed("input_1", input)
      .feed("keras_learning_phase", learningPhase)
      .fetch("dense_1/Softmax")
      .run().get(0)
    val floatBuffer = FloatBuffer.allocate(12)
    value.writeTo(floatBuffer)
    println(floatBuffer.array().toList)
    val res_bytes = value.dataType()

    learningPhase.shape()
  }

  def cleanup() : Unit = {
    ls.close()
  }

}


object SeedlingsModelServing {

  def main(args: Array[String]): Unit = {
    val model_path = "C:\\Users\\michal\\IdeaProjects\\flink-seedling-processor\\src\\main\\resources\\data\\frozen_SeedlingsRecognize.pb" // model

    val matSrc = opencv_imgcodecs.imread("C:\\Users\\michal\\IdeaProjects\\flink-seedling-processor\\src\\main\\resources\\data\\0b1df6f5a.png")
    val matDst = new Mat()
    opencv_imgproc.resize(matSrc, matDst, new Size(300, 300))

    val darr: Array[Byte] = new Array[Byte]((matDst.total() * matDst.channels()).toInt)
    matDst.data().get(darr)

    val normalized: Array[Byte] = darr.map(b => b.toFloat / 255).flatMap(ByteBuffer.allocate(4).putFloat(_).array())
    val lmodel = new SeedlingsModelServing(model_path)
    lmodel.scorePic(normalized)
    lmodel.cleanup()
  }

  private def readGraph(path: Path): Graph = {
    val graphData = Files.readAllBytes(path)
    val g = new Graph
    g.importGraphDef(graphData)
    g
  }
}