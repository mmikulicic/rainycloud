import sbt._
//import eu.dnetlib.DoccoPlugin

class RainyCloudProject(info: ProjectInfo) extends DefaultProject(info) {
  val log4j = "log4j" % "log4j" % "1.2.16"

	//val scromiumRepo = "Cliff's Scromium Repo" at "http://cliffmoon.github.com/scromium/repository/"

	//	val codaRepo = "Coda Hale's Repository" at "http://repo.codahale.com/"
  //val scromium = "scromium" % "scromium_2.8.0" % "0.6.4" // artifacts Artifact("scromium-all_2.8.0", "all", "jar")



  override def managedStyle = ManagedStyle.Maven
 
  lazy val publishTo = Resolver.url("RI Releases", new java.net.URL("http://maven.research-infrastructures.eu/nexus/content/repositories/snapshots/"))
  Credentials(Path.userHome / ".ivy2" / ".credentials", log)

  val riReleases = "RI Releases" at "http://maven.research-infrastructures.eu/nexus/content/repositories/releases"
  val scalaToolsSnapshots = "Scala-Tools Maven2 Snapshots Repository" at "http://scala-tools.org/repo-snapshots"
	val codaRepo = "Coda Hale's Repository" at "http://repo.codahale.com/"
  val fuseRepo = "fuse repo" at "http://repo.fusesource.com/maven2-all/"

  val specsdep = "org.scala-tools.testing" %% "specs" % "1.6.7.2" % "test->default"
  val mockito = "org.mockito" % "mockito-all" % "1.8.5"

	val metrics = "com.yammer" %% "metrics" % "1.0.7" withSources()
  val guice2 = "com.google.inject" % "guice" % "2.0"
  val guiceScala = "uk.me.lings" % "scala-guice_2.8.0" % "0.1"

  val opencsv = "net.sf.opencsv" % "opencsv" % "2.1"
  //val flatpack = "net.sf.flatpack" % "flatpack" % "3.1.1"
  val supercsv = "org.supercsv" % "supercsv" % "1.20"
}
