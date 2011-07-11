package it.cnr.aquamaps
import com.google.gson.Gson
import net.lag.configgy.Configgy

import org.zeromq.ZMQ
import scala.actors.Futures._

import akka.agent.Agent
import akka.actor.Actor
import akka.actor.Actor._
import akka.actor.ActorRef
import akka.dispatch.Dispatchers
import akka.util.duration._
import akka.actor.ReceiveTimeout

import scala.collection.immutable.HashMap
import scala.collection.immutable.Queue

import net.lag.logging.Logger

class ZeromqTaskExecutor(val name: String) extends ZeromqHandler with ZeromqJobSubmitterExecutorCommon {
  import Zeromq._

  private val log = Logger(classOf[ZeromqTaskExecutor])

  case class Submit(val task: TaskRef)

  val worker = actorOf(new WorkerActor()).start()

  val socket = context.socket(ZMQ.XREQ)

  new Thread(() => {

    socket.setIdentity(name)
    socket.bind("inproc://executor_%s".format(name))
    socket.connect("tcp://%s:%s".format(Configgy.config.getString("queue-host").getOrElse("localhost"),Configgy.config.getInt("queue-port").getOrElse(5566)))

    log.info("registering %s".format(name))
    send("READY")

    val poller = context.poller()
    poller.register(socket, ZMQ.Poller.POLLIN)

    def eventLoop(): Unit = {
      while (true) {
        val res = poller.poll(1000 * 1000)
        if (res > 0) {

          log.debug("W %s got poll in".format(name))

          val address = getAddress()
          recv() match {
            case "KILL" =>
              log.info("W %s was shot in the head, dying".format(name))
              return
            case "SUBMIT" =>
              val task = recv()
              log.debug("W %s got submission '%s'".format(name, task))
              worker ! Submit(TaskRef(task))
              log.debug("worker actor messaged")
          }

        }

        send("HEARTBEAT")
      }
    }

    eventLoop()
    log.warning("W %s died".format(name))
  }).start()

  var taskRunning = false

  def executeTask(task: TaskRef): Unit = {
    taskRunning = true

    log.info("W %s will spawn background task".format(name))
    spawn {
      log.debug("W %s is working for real (mumble mumble)".format(name))
      //        log.debug("W %s is working on step %s of task %s".format(name, i, task))
      for(i <- 1 to 40) {
        Thread.sleep(100)
        worker ! Progress(task, 551, 100)
      }
      //}
      log.info("W %s finished computing task %s".format(name, task))
      worker ! Finish(task)
    }
  }

  class WorkerActor extends Actor {
    private val log = Logger(classOf[WorkerActor])

    self.dispatcher = Dispatchers.newThreadBasedDispatcher(self, 15, 100.milliseconds)

    val innerSocket = context.socket(ZMQ.XREQ)

    override def preStart() = {
      log.debug("pre start WorkerActor %s".format(name))

      innerSocket.setIdentity(name + "_b")
      innerSocket.connect("tcp://localhost:5566")

      self.receiveTimeout = Some(2000L)
    }

    /*# It could happen that we sent our "ready" but no server was listening,
     * we can resend the "READY" message once in a while, just make sure we are not
     * executing something right now. */
    def perhapsRecover() = {
      if (!taskRunning)
        send(innerSocket, "READY")
    }

    def finished(task: TaskRef) = {
      taskRunning = false

      send(innerSocket, "SUCCESS", task.id)
      send(innerSocket, "READY")
    }

    val gson = new Gson

    def trackProgress(progress: Progress) = {
      send(innerSocket, "PROGRESS", gson.toJson(progress))
    }

    def receive = {
      case Submit(task) => log.debug("submitting to execute %s".format(task)); executeTask(task)
      case progress: Progress => trackProgress(progress) 
      case Finish(task) => log.debug("sending back ready"); finished(task);
      case ReceiveTimeout => log.debug("ww %s inner control timed out".format(name)); perhapsRecover()
    }

    override def postStop() = {
      log.warning("Stopping WorkerActor %s".format(name))
    }

  }

  def send(msg: String): Unit = send(socket, msg)

  def send(socket: ZMQ.Socket, msg: String) = {
    log.debug("SENDING %s from %s".format(msg, name));
    sendParts(socket, name, "", msg)
  }

  // TODO: cleanup
  def send(socket: ZMQ.Socket, msg: String, arg: String) = {
    log.debug("SENDING %s from %s".format(msg, name));
    sendParts(socket, name, "", msg, arg)
  }

}

case class TaskRef(val id: String)
case class Progress(val task: TaskRef, amount: Long, delta: Long)