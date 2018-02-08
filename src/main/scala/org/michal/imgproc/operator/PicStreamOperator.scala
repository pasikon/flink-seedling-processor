package org.michal.imgproc.operator

import org.apache.flink.streaming.api.operators.{AbstractStreamOperator, OneInputStreamOperator}
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord
import org.bytedeco.javacpp.opencv_core.{Mat, Size}
import org.bytedeco.javacpp.{opencv_imgcodecs, opencv_imgproc}

class PicStreamOperator extends AbstractStreamOperator[Array[Byte]] with OneInputStreamOperator[String, Array[Byte]]{

  override def processElement(element: StreamRecord[String]): Unit = {
    val path = element.getValue
    val matSrc = opencv_imgcodecs.imread(path)
    val matDst = new Mat()
    opencv_imgproc.resize(matSrc, matDst, new Size(300, 300))

    val darr: Array[Byte] = new Array[Byte]((matDst.total() * matDst.channels()).toInt)
    matDst.data().get(darr)

    output.collect(new StreamRecord[Array[Byte]](darr))
  }



}
