package org.tensorpol.ai_seedling_rec.imageprocessing

import java.nio.ByteBuffer

import com.tensorpol.common.domain.Msg
import com.tensorpol.seedlingsinterface.domain.{SeedlingClassifyRequest, SeedlingImage}
import org.apache.flink.api.common.functions.MapFunction
import org.bytedeco.javacpp.opencv_core.Size
import org.bytedeco.javacpp.{BytePointer, opencv_imgcodecs, opencv_imgproc}
import org.slf4j.{Logger, LoggerFactory}
import org.tensorpol.ai_seedling_rec.imageprocessing.helper.ImgResizeInput

class ImageResizeMapFunction extends MapFunction[ImgResizeInput, Msg[SeedlingClassifyRequest]]{

  val log: Logger = LoggerFactory.getLogger(classOf[HdfsPathToByteArrMapFunction])

  override def map(inpImg: ImgResizeInput): Msg[SeedlingClassifyRequest] = {

    import org.bytedeco.javacpp.opencv_core.Mat
    val inpImgBytes: Array[Byte] = inpImg.imageReq.payload.image.img_bytes
    log.info(s"Processing Msg with Correlation ID: ${inpImg.imageReq.id} : Resizing image, size: ${inpImgBytes.length}")

    val mat = new Mat(new BytePointer(ByteBuffer.wrap(inpImgBytes)))
    val matSrc = opencv_imgcodecs.imdecode(mat, opencv_imgcodecs.CV_LOAD_IMAGE_UNCHANGED)

    val matDst = new Mat()
    opencv_imgproc.resize(matSrc, matDst, new Size(inpImg.xTarget, inpImg.yTarget))

    val allocArr: Array[Byte] = new Array[Byte]((matDst.total() * matDst.channels()).toInt)

    matDst.data().get(allocArr)
    Msg(SeedlingClassifyRequest(SeedlingImage(allocArr)), inpImg.imageReq)
}

}
