package it.cnr.aquamaps

import com.google.inject.Module
import net.lag.configgy.Config
import net.lag.configgy.Configgy
import cloud.WebModule

trait RainyCloudModule {
  val conf = if (Configgy.config != null) Configgy.config else Config.fromString("")
}

object Modules {
  val modules = Map("BabuDB" -> BabuDBModule(),
    "COMPSs" -> COMPSsModule(),
    "COMPSsObject" -> COMPSsObjectModule(),
    "RandomAlgo" -> RandomAlgoModule(),
    "HDFS" -> HDFSModule(),
    "Web" -> WebModule())

  def enabledModules(conf: Config): Seq[Module] = for {
    name <- conf.getList("modules")
    module <- modules.get(name)
  } yield module

}
