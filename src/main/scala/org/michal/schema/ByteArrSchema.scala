package org.michal.schema

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor

class ByteArrSchema extends org.apache.flink.api.common.serialization.DeserializationSchema[Array[Byte]] with org.apache.flink.api.common.serialization.SerializationSchema[Array[Byte]] {

  private val serialVersionUID: Long = 4354325231l

  override def isEndOfStream(nextElement: Array[Byte]): Boolean = false

  override def deserialize(message: Array[Byte]): Array[Byte] = message

  override def serialize(element: Array[Byte]): Array[Byte] = element

  override def getProducedType: TypeInformation[Array[Byte]] =
    TypeExtractor.getForClass(classOf[Array[Byte]])
}
