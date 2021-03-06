package it.cnr.aquamaps

import com.google.inject._
import com.google.inject.name._

import org.xtreemfs.babudb._
import org.xtreemfs.babudb.config._
import org.xtreemfs.babudb.log._
import org.xtreemfs.babudb.lsmdb._

import java.io._
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

import com.google.inject._
import uk.me.lings.scalaguice.InjectorExtensions._
import com.google.inject.name._
import com.google.inject.name.Names.named
import uk.me.lings.scalaguice.ScalaModule
import com.weiglewilczek.slf4s.Logging

import com.weiglewilczek.slf4s.Logging

import HCAF._
import HSPEN._

import net.lag.configgy.Configgy

case class BabuDBModule() extends AbstractModule with ScalaModule {
  def configure() {
    bind[Loader[HSPEN]].annotatedWith(named("forBabu")).to(classOf[TableHSPENLoader]).in(classOf[Singleton])

    //bind[Serializer[HCAF]].toInstance(AvroSerializer[HCAF]())
    bind[Serializer[HCAF]].toInstance(new SerializableSerializer[HCAF]())
    //bind[Serializer[HSPEN]].toInstance(AvroSerializer[HSPEN]())
    bind[Serializer[HSPEN]].toInstance(new SerializableSerializer[HSPEN]())
  }

  @Provides
  @Singleton
  def hcafFetcher(loader: Loader[HCAF], serializer: Serializer[HCAF]): Fetcher[HCAF] = new BabuDBFetcher("hcaf", loader, serializer)

  @Provides
  @Singleton
  def hspenLoader(@Named("forBabu") loader: Loader[HSPEN], serializer: Serializer[HSPEN]): Loader[HSPEN] = new BabuDBLoader("hspen", loader, serializer)
}


trait BabuDB[A <: Keyed] extends Logging {

  val loader: Loader[A]
  val dbName: String
  val serializer: Serializer[A]

  val dataPath = Configgy.config.getString("babudbdir").getOrElse("/tmp/babudb")

  val databaseSystem = BabuDBFactory.createBabuDB(new ConfigBuilder().setDataPath(dataPath).setLogAppendSyncMode(DiskLogger.SyncMode.ASYNC).build())
  val dbman = databaseSystem.getDatabaseManager();

  val db = getDb

  def stop {
    db.shutdown
    databaseSystem.shutdown()
  }

  def getDb() = {
    try {
      dbman.createDatabase(dbName, 1)
      val db = dbman.getDatabase(dbName);
      reload(db)
      databaseSystem.getCheckpointer().checkpoint()
      db
    } catch {
      case _: BabuDBException => dbman.getDatabase(dbName);
    }
  }

  def reload(db: Database) = {
    logger.info("caching %s ...".format(loader))
    for (record <- loader.load)
      db.singleInsert(0, record.key.getBytes, serializer.serialize(record), null)
    logger.info("done")
  }
}


/*!
 Embedded key/value store. More efficient than naive memory db, but local, thus in our scenario the initial data has to be loaded from another loader.
 */
class BabuDBFetcher[A <: Keyed] @Inject() (val dbName: String, val loader: Loader[A], val serializer: Serializer[A]) extends Fetcher[A] with BabuDB[A] {

  def fetch(start: String, size: Long) = {
    val res = db.prefixLookup(0, start.getBytes, null)
    res.get.take(size.toInt).map { x => serializer.deserialize(x.getValue) }.toIterable
  }

  override def shutdown = stop
}

class BabuDBLoader[A <: Keyed] @Inject() (val dbName: String, val loader: Loader[A], val serializer: Serializer[A]) extends Loader[A] with BabuDB[A] {

  def load = {
    val res = db.prefixLookup(0, "".getBytes, null)
    res.get.map { x => serializer.deserialize(x.getValue) }.toIterable
  }

  override def shutdown = stop
}
