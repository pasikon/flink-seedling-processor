package org.tensorpol.ai_seedling_rec.model_reader

import java.nio.file.{Files, Path, Paths}

import org.apache.flink.core.fs
import org.apache.flink.runtime.fs.hdfs.{HadoopDataInputStream, HadoopFileSystem}
import org.apache.flink.shaded.guava18.com.google.common.io.ByteStreams
import org.tensorflow.Graph
import org.tensorpol.ai_seedling_rec.hdfsutils.HdfsUtils

object TensorFlowGraphProvider {

  def fromHDFS(): Graph = {
    val flinkHadoopFileSystem: HadoopFileSystem = HdfsUtils.getFlinkHadoopFileSystem

    //TODO: externalize model location
    val modelDir = "tf_models/"
    val modelFile = "frozen_SeedlingsRecognize.pb"
    val hadoopDataInputStream: HadoopDataInputStream = flinkHadoopFileSystem.open(new fs.Path(modelDir, modelFile))

    val modelBytes: Array[Byte] = ByteStreams.toByteArray(hadoopDataInputStream.getHadoopInputStream.getWrappedStream)
    graphFromBytes(modelBytes)
  }

  def fromLocalFilesystem(): Graph = {
    //TODO: externalize model location
    val path = "data/frozen_SeedlingsRecognize.pb" // model
    println(s"Loading saved model from $path")
    readGraph(Paths.get (path))
  }

  private def readGraph(path: Path): Graph = {
    val graphData: Array[Byte] = Files.readAllBytes(path)
    graphFromBytes(graphData)
  }

  def graphFromBytes(graphData: Array[Byte]): Graph = {
    val g = new Graph
    g.importGraphDef(graphData)
    g
  }

}
