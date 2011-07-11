package it.cnr.aquamaps.cloud

import it.cnr.aquamaps._

import net.lag.logging.Logger
import net.lag.configgy.{ Config, Configgy }

import akka.agent.Agent

object Submitter {
  private val log = Logger(Submitter getClass)

  val js = new ZeromqJobSubmitter()


  val jobs = Agent(Map[String, JobSubmitter.Job]())
  def workers = js.workers

  def queueLength = js.queueLength
  
  def registerJob(job: JobSubmitter.Job) = {
    jobs send (_ + ((job.id, job)))
    job.id
  }

  def deleteJob(id: String) = {
    jobs send (_ - id)
  }

  def killJob(id: String) = {
//    jobs send (_ - id)
  }


  def init = {
    println("IIIIIIIIIIIIIIIIIINITIALIZZING %s".format(js))
  }

}

object SubmitterTester extends App {
  private val log = Logger(Submitter getClass)


  if (!Configgy.config.getBool("web").getOrElse(false)) {
    Thread.sleep(4000)
    log.info("SENDING COMMAND storm")

    runTest()
  }


  def spawnTest() = {
    val job = Submitter.js.newJob()
    for (i <- 1 to 100)
      job.addTask(Submitter.js.newTaskSpec("wow" + i))
    job.seal()
    Submitter.registerJob(job)
    job
  }

  def runTest() {
    val job = spawnTest()
      
    Thread.sleep(1000)
    log.info(">>>>>>>>>>>>>>>>>>>> Polling for status Checking total tasks")
    log.info(">>>>>>>>>>>>>>>>>>>> Total job tasks %s, completed tasks %s. Completed ? %s".format(job.totalTasks, job.completedTasks, job.completed))
    while (!job.completed) {
      Thread.sleep(1000)
      log.info("Total job tasks %s, completed tasks %s. Completed ? %s".format(job.totalTasks, job.completedTasks, job.completed))
    }
    log.info(">>>>>>>>>>>>>>>>>>>> Total job tasks %s, completed tasks %s. Completed ? %s".format(job.totalTasks, job.completedTasks, job.completed))
        
  }

}
