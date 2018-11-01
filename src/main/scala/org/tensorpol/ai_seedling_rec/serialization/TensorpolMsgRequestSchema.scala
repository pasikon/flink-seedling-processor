package org.tensorpol.ai_seedling_rec.serialization

import com.tensorpol.common.domain.{Msg, Request}
import com.tensorpol.seedlingsinterface.domain.SeedlingsInterfaceRequest
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor

class TensorpolMsgRequestSchema extends org.apache.flink.api.common.serialization.DeserializationSchema[Msg[Request]] with org.apache.flink.api.common.serialization.SerializationSchema[Msg[Request]] {

  override def deserialize(message: Array[Byte]): Msg[Request] = Msg(message, SeedlingsInterfaceRequest.payloadInf)

  override def isEndOfStream(nextElement: Msg[Request]): Boolean = false

  override def serialize(element: Msg[Request]): Array[Byte] = element.toProto(SeedlingsInterfaceRequest.protoInf).toByteArray

  override def getProducedType: TypeInformation[Msg[Request]] = TypeExtractor.getForClass(classOf[Msg[Request]])

}
