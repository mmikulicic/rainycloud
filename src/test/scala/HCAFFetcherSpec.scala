package it.cnr.aquamaps

import org.specs._

import com.google.inject.Guice
import com.google.inject._
import uk.me.lings.scalaguice.InjectorExtensions._
import com.google.inject.name._
import uk.me.lings.scalaguice.ScalaModule

import org.specs.mock.Mockito
import org.mockito.Matchers._ // to use matchers like anyInt()

object HCAFFetcherSpec extends Specification with Mockito {
  "HCAF fetcher" should {
    "fetch from csv" in {
      case class TestModule() extends AbstractModule with ScalaModule {
        def configure() {
          bind[HCAFLoader].to[TableHCAFLoader]
          bind[TableReader[HCAF]].toInstance(new FileSystemTableReader("data/hcaf.csv.gz"))
          bind[PositionalStore[HCAF]].to[CSVPositionalStore[HCAF]]

          bind[Loader[HCAF]].to[HCAFLoader]
          
          bind[Fetcher[HCAF]].to[MemoryFetcher[HCAF]]
        }
      }

      val injector = Guice createInjector TestModule()
      val fetcher = injector.instance[Fetcher[HCAF]]

      val rows = fetcher.fetch("1000", 231)
      println("rows %s".format(rows))

      rows.size must be_==(231)
    }
  }
}
