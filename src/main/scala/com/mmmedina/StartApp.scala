package com.mmmedina

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import com.mmmedina.actors.OrganizationActor
import com.mmmedina.httpClient.GithubClient
import com.mmmedina.routes.OrganizationsRoutes

import scala.util.{Failure, Success}

//#main-class
object StartApp {
  //#start-http-server
  private def startHttpServer(routes: Route)(implicit system: ActorSystem[_]): Unit = {
    import system.executionContext

    val futureBinding = Http().newServerAt("localhost", 8083).bind(routes)
    futureBinding.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info("Server online at http://{}:{}/", address.getHostString, address.getPort)
      case Failure(ex)      =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
  }
  def main(args: Array[String]): Unit = {
    lazy val rootBehavior = Behaviors.setup[Nothing] { context =>
      implicit val actorSystem: ActorSystem[Nothing] = context.system
      val githubClient                               = new GithubClient()
      val organizationActor                          = context.spawn(OrganizationActor(githubClient), "ContributorRegistryActor")
      context.watch(organizationActor)

      val routes = new OrganizationsRoutes(organizationActor)(context.system)
      startHttpServer(routes.organizationRoutes)(context.system)

      Behaviors.empty
    }
    ActorSystem[Nothing](rootBehavior, "HelloAkkaHttpServer")
  }
}
