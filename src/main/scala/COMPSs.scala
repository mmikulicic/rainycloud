package it.cnr.aquamaps

import com.google.inject._
import scala.xml.{ Node, Text, XML }
import scala.xml.Utility.trim
import io.Source.fromFile
import java.io.File
import com.google.inject._
import com.google.inject.util.{ Modules => GuiceModules }
import uk.me.lings.scalaguice.InjectorExtensions._
import uk.me.lings.scalaguice.ScalaModule
import Watch.timed
import java.io._
import org.apache.commons.io.IOUtils
import resource._

/*!# COMPSs support

 COMPSs currently only support files as communication method between remote spawns.

*/

/*! Hencefore we needa way to serialize Partitions to and from xml: this import adds the `toXml` and `toPartition` methods to `Partition` and `xml.Node` respectively, see bottom. */
import P2XML._

/*! In order to connect to the rest of the system, first we implement the `Generator` interface. We receive partitions from the entry point here, convert the parameters
 * into files, amd delegate to another interface whose signature COMPSs knowns how to handle (files as parameters). */
class COMPSsGenerator @Inject() (val delegate: FileParamsGenerator, val emitter: COMPSsCollectorEmitter[HSPEC]) extends Generator {

  def computeInPartition(p: Partition) {
    val tmpFile = mkTmp
    XML.save(tmpFile, p.toXml)

    val outputFile = delegate.computeInPartition(tmpFile)

    /*! Keep it for later */
    emitter.add(outputFile)
  }

  /*! Well this is a rather stupid way to merge the remote output into our single result. `Emitter` should be extended to support bulk emits. */
  def slowMerge(outputFile: String) {
    val loader = new TableHSPECLoader(new CSVPositionalSource(new FileSystemTableReader(outputFile)))

    for (hspec <- loader.load)
      emitter.emit(hspec)
  }

  def mkTmp = {
    val file = File.createTempFile("rainycloud", ".xml")
    file.deleteOnExit()
    file.toString
  }
}

/*! We would like to defer the merging of the results until we spawned all the tasks */
class COMPSsCollectorEmitter[A] @Inject() (val outputFileName: String) extends Emitter[A] {
  var list: List[String] = List()

  def emit(record: A) = throw new IllegalArgumentException("this emitter cannot be used directly")

  def add(fileName: String) = list = fileName :: list

  /*! The actual merging is invoked upon emitter flush, which is called at the end of the job. */
  def flush {
    println("merging results into %s".format("outputFileName"))
    val output = new FileSystemTableWriter(outputFileName)
    timed("merging") {
      for {
        fw <- managed(output.writer)
        file <- list
      } merge(file, fw)
    }
  }

  /*! We have to call a static method for COMPSs to be able to track this call and managed dependencies. */
  def merge(fileName: String, out: Writer) {
    COMPSsCollectorEmitter.merge(fileName, out)
  }
}

object COMPSsCollectorEmitter {
  /*! We assume we can perform a nice low level concatenation of the parts. */
  def merge(fileName: String, out: Writer) {
    timed("merging %s".format(fileName)) {
      for (in <- managed(new FileReader(fileName)))
        IOUtils.copy(in, out)
    }
  }
}

/*! Now here's the magic. This method accepts files and converts them back to our native parameters and delegates to another Generator (I still don't know if we have to pass java `Files` or file names.) */
trait FileParamsGenerator {
  def computeInPartition(fileName: String): String
}

/*! The `FileParamsGenerator` above is just an abstract trait, we need a way to find the backend generator.
 If we are running within a real application it's easy: just let Guice inject it! */
class SimpleFileParamsGenerator @Inject() (val delegate: Generator, val emitter: Emitter[HSPEC], val writer: FileSystemTableWriter[HSPEC]) extends FileParamsGenerator {
  def computeInPartition(fileName: String): String = {
    delegate.computeInPartition(XML.load(fileName).toPartition)
    emitter.flush
    writer.name
  }
}

/*! But what if COMPSs requires only static method invocations because it wouldn't know how to spawn the instances on the remote worker ? */
class StaticFileParamsGenerator extends FileParamsGenerator {
  def computeInPartition(fileName: String): String = StaticFileParamsGenerator.staticDelegate(fileName)
}

/*! The static delegate also returns the output filename so that COMPSs can move the data for us. */
object StaticFileParamsGenerator {

  case class COMPSsWorkerModule() extends AbstractModule with ScalaModule {
    def configure() {
      /*! Let's write to a temporary file, so that the same machine can host several instances of this worker. In order to do this
       we override the Guice config and inject another TableWriter. */
      val writer: FileSystemTableWriter[HSPEC] = new FileSystemTableWriter(mkTmp)

      bind[TableWriter[HSPEC]].toInstance(writer)
      bind[FileSystemTableWriter[HSPEC]].toInstance(writer)

      /*! The static method will just delegate the work to the `SimpleFileParamsGenerator` */
      bind[FileParamsGenerator].to[SimpleFileParamsGenerator]
    }

    def mkTmp = {
      val file = File.createTempFile("rainycloud-worker-", ".csv")
      file.deleteOnExit()
      file.toString
    }
  }

  /*! We have to create a new DI context, since we run in a static method (and possibly on another machine, in a completely disconnected runtime context) */
  def injector = Guice createInjector (GuiceModules `override` AquamapsModule() `with` (COMPSsWorkerModule(), BabuDBModule()))

  def staticDelegate(fileName: String): String = {
    withInjector { injector =>
      val generator = injector.instance[FileParamsGenerator]
      generator.computeInPartition(fileName)
    }
  }

  /*! Currently Guice has no support for shutting down an injector, so we have to do it manually */
  def withInjector[A](body: Injector => A) = {
    val i = injector
    val res = body(i)
    i.instance[Fetcher[HCAF]].shutdown
    i.instance[Loader[HSPEN]].shutdown
    res
  }
}

/*!## Serialization

 These are details, we need some (de)serialization for partition descriptors. Let's go for a readable choice: */

class P2XML(val p: Partition) {
  def toXml() = {
    <partition>
      <start>{ p.start }</start>
      <size>{ p.size }</size>
    </partition>
  }
}

class XML2P(val p: Node) {
  def toPartition: Partition = trim(p) match {
    case <partition><start>{ start }</start><size>{ size }</size></partition> => new Partition(start.text, size.text.toLong)
  }
}

object P2XML {
  implicit def p2xml(p: Partition): P2XML = new P2XML(p)
  implicit def p2xml(p: Node): XML2P = new XML2P(p)
}
