package com.mmmedina.routes

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes.{Conflict, InternalServerError, NotFound, Unauthorized}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.util.Timeout
import com.mmmedina.actors.OrganizationActor
import com.mmmedina.actors.OrganizationActor._
import com.mmmedina.serialization.JsonFormats

import scala.util.{Failure, Success}

class OrganizationsRoutes(contributorRegistry: ActorRef[OrganizationActor.Command])(implicit val system: ActorSystem[_])
    extends JsonFormats
    with SprayJsonSupport {

  private implicit val timeout: Timeout =
    Timeout.create(system.settings.config.getDuration("full-akka-app.routes.ask-timeout"))

  // exceptions handler
  def myExceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case ex: NoSuchElementException        =>
        complete(HttpResponse(NotFound, entity = s"message: ${ex.getMessage}"))
      case ex: InterruptedException          =>
        complete(HttpResponse(InternalServerError, entity = s"message: ${ex.getMessage}"))
      case ex: UnsupportedOperationException =>
        complete(HttpResponse(Unauthorized, entity = s"message: ${ex.getMessage}"))
      case ex: RuntimeException              =>
        complete(HttpResponse(Conflict, entity = s"message: ${ex.getMessage}"))
    }

  // endpoints
  val healthRoute: Route =
    path("health") {
      get {
        complete("OK")
      }
    }

  val contributorsRoute: Route = {
    pathPrefix("org") {
      concat(path(Segment / "contributors") { organizationName =>
        concat(get {
          rejectEmptyResponse {
            onComplete(contributorRegistry.askWithStatus(GetContributors(organizationName, _))) {
              case Success(dto)   =>
                complete(dto.contributors)
              case Failure(error) =>
                throw error
            }
          }
        })
      })
    }
  }

  val applicationRoutes: Route = {
    handleExceptions(myExceptionHandler) {
      concat(healthRoute, contributorsRoute)
    }
  }
}
