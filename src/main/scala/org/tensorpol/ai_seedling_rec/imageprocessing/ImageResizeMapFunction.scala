package org.tensorpol.ai_seedling_rec.imageprocessing

import java.nio.ByteBuffer
import scala.util.Try

import org.apache.flink.api.common.functions.MapFunction
import org.apache.flink.core.fs
import org.apache.flink.shaded.guava18.com.google.common.io.ByteStreams
import org.bytedeco.javacpp.opencv_core.Size
import org.bytedeco.javacpp.{BytePointer, opencv_imgcodecs, opencv_imgproc}
import org.slf4j.{Logger, LoggerFactory}
import org.tensorpol.ai_seedling_rec.imageprocessing.helper.ImgResizeInput
import org.tensorpol.ai_seedling_rec.hdfsutils.HdfsUtils

class ImageResizeMapFunction extends MapFunction[ImgResizeInput, Array[Byte]]{

  val log: Logger = LoggerFactory.getLogger(classOf[HdfsPathToByteArrMapFunction])

  override def map(inpImg: ImgResizeInput): Array[Byte] = {

    import org.bytedeco.javacpp.opencv_core.Mat
    val inpImgBytes: Array[Byte] = inpImg.image
    log.info(s"Resizing image, size: ${inpImgBytes.length}")

    val mat = new Mat(new BytePointer(ByteBuffer.wrap(inpImgBytes)))
    val matSrc = opencv_imgcodecs.imdecode(mat, opencv_imgcodecs.CV_LOAD_IMAGE_UNCHANGED)

    val matDst = new Mat()
    opencv_imgproc.resize(matSrc, matDst, new Size(inpImg.xTarget, inpImg.yTarget))

    val allocArr: Array[Byte] = new Array[Byte]((matDst.total() * matDst.channels()).toInt)

    matDst.data().get(allocArr)
    allocArr
  }

}
