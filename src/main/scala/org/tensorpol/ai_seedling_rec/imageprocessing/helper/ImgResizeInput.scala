package org.tensorpol.ai_seedling_rec.imageprocessing.helper

import com.tensorpol.common.domain.Msg
import com.tensorpol.seedlingsinterface.domain.SeedlingClassifyRequest

case class ImgResizeInput(xTarget: Int, yTarget: Int, imageReq: Msg[SeedlingClassifyRequest])
