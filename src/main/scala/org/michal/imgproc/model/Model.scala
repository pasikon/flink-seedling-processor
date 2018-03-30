package org.michal.imgproc.model

import java.nio.ByteBuffer

case class TFModel(name: String, model: Array[Byte])
case class SeedlingPicture(bytes: Array[Byte])

object SeedlingPicture {
  /**
    * Normalizes input picture
    * @param arg
    * @return
    */
  def apply(arg: Array[Byte], normFac: Int): SeedlingPicture = new SeedlingPicture(
    arg.map(b => b.toFloat / normFac).flatMap(ByteBuffer.allocate(4).putFloat(_).array())
  )
}
