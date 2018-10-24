package org.tensorpol.ai_seedling_rec.hdfsutils

import java.net.URI

import org.apache.flink.runtime.fs.hdfs.HadoopFileSystem
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem

object HdfsUtils {

  def getFlinkHadoopFileSystem = {
    val hadoopConf = new Configuration()
    hadoopConf.set("hadoop.http.staticuser.user", "root")
    hadoopConf.set("hadoop.home.dir", "/")
    hadoopConf.set("fs.defaultFS", "hdfs://namenode:8020")
    val hdfsURI = "hdfs://namenode:8020"
    //    val path = new Path(hdfsURI)
    val hdfs = FileSystem.get(URI.create(hdfsURI), hadoopConf)
    val flinkHadoopFileSystem = new HadoopFileSystem(hdfs)
    flinkHadoopFileSystem
  }

}
