package org.tensorpol.ai_seedling_rec.imageprocessing

import java.nio.ByteBuffer
import scala.util.Try

import org.apache.flink.api.common.functions.MapFunction
import org.apache.flink.core.fs
import org.apache.flink.shaded.guava18.com.google.common.io.ByteStreams
import org.bytedeco.javacpp.opencv_core.Size
import org.bytedeco.javacpp.{BytePointer, opencv_imgcodecs, opencv_imgproc}
import org.slf4j.{Logger, LoggerFactory}
import org.tensorpol.ai_seedling_rec.hdfsutils.HdfsUtils


class HdfsPathToByteArrMapFunction extends MapFunction[String, Array[Byte]] {
  val log: Logger = LoggerFactory.getLogger(classOf[HdfsPathToByteArrMapFunction])

  override def map(filename: String): Array[Byte] = {
    val fhdfs = HdfsUtils.getFlinkHadoopFileSystem

    val hadoopDataInputStream = Try(fhdfs.open(new fs.Path("seedling_pics/", filename)))
      .getOrElse{
        log.error("Image not found falling back to default image!")
        fhdfs.open(new fs.Path("seedling_pics/", "0911d3dee.png"))
      }
    //      val img: BufferedImage = ImageIO.read(hadoopDataInputStream.getHadoopInputStream.getWrappedStream)

    import org.bytedeco.javacpp.opencv_core.Mat
    val bytes: Array[Byte] = ByteStreams.toByteArray(hadoopDataInputStream.getHadoopInputStream.getWrappedStream)
    log.info(s"Read ${bytes.length} inpImgBytes for $filename from HDFS...")
    val mat = new Mat(new BytePointer(ByteBuffer.wrap(bytes)))
    val matSrc = opencv_imgcodecs.imdecode(mat, opencv_imgcodecs.CV_LOAD_IMAGE_UNCHANGED)

    val matDst = new Mat()
    opencv_imgproc.resize(matSrc, matDst, new Size(300, 300))

    val darr: Array[Byte] = new Array[Byte]((matDst.total() * matDst.channels()).toInt)
    matDst.data().get(darr)
    darr
  }
}
