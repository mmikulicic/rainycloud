package it.cnr.aquamaps

import org.scalatra._
import org.scalatra.scalate._

import org.mortbay.jetty.Server
import org.mortbay.jetty.servlet.{ Context, ServletHolder }

import net.lag.logging.Logger
import net.lag.configgy.{ Config, Configgy }
import javax.servlet.Servlet

object WebServer {
  val log = Logger(WebServer getClass)
  
  def run(servlets: Map[String, Servlet]) {
    val port = Configgy.config.getInt("web-port").getOrElse(8780)

    val server = new Server(port)
    val root = new Context(server, "/", Context.SESSIONS)
    for ((route, servlet) <- servlets)
      root.addServlet(new ServletHolder(servlet), route)
    server.start()

    log.info("web server started on %s".format(port))
  }
}
