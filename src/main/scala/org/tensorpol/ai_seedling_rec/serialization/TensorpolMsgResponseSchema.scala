package org.tensorpol.ai_seedling_rec.serialization

import com.tensorpol.common.domain.{Msg, Response}
import com.tensorpol.seedlingsinterface.domain.SeedlingsInterfaceResponse
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor

class TensorpolMsgResponseSchema extends org.apache.flink.api.common.serialization.DeserializationSchema[Msg[Response]] with org.apache.flink.api.common.serialization.SerializationSchema[Msg[Response]] {

  override def deserialize(message: Array[Byte]): Msg[Response] = Msg(message, SeedlingsInterfaceResponse.payloadInf)

  override def isEndOfStream(nextElement: Msg[Response]): Boolean = false

  override def serialize(element: Msg[Response]): Array[Byte] = element.toProto(SeedlingsInterfaceResponse.protoInf).toByteArray

  override def getProducedType: TypeInformation[Msg[Response]] = TypeExtractor.getForClass(classOf[Msg[Response]])

}
