package com.mmmedina.actors.githubAPI

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.pattern.StatusReply
import akka.pattern.StatusReply.Error
import com.mmmedina.models.Repository
import com.mmmedina.serialization.JsonFormats
import com.mmmedina.httpClient.GithubClient

import scala.util.{Failure, Success}

object RepositoriesActor extends JsonFormats with SprayJsonSupport {
  case class Repositories(repositories: Seq[Repository])

  sealed trait Command

  final case class GetByOrganization(organizationName: String, replyTo: ActorRef[StatusReply[Repositories]])
      extends Command

  private final case class ErrorReply(error: Throwable, replyTo: ActorRef[StatusReply[Repositories]]) extends Command

  private final case class ReplyWithRepositories(
      repositories: Seq[Repository],
      replyTo: ActorRef[StatusReply[Repositories]]
  ) extends Command

  def apply(githubAPI: GithubClient): Behavior[Command] = {
    Behaviors.setup { context =>
      Behaviors.receiveMessage {

        case GetByOrganization(organizationName, replyTo) =>
          context.log.info(
            s"Searching repositories by organization name: $organizationName. Sending request to Github API."
          )
          val responseFuture = githubAPI.getRepositories(organizationName)
          context.pipeToSelf(responseFuture) {
            case Success(repositories) =>
              ReplyWithRepositories(repositories, replyTo)
            case Failure(exception)    =>
              context.log.error(exception.getMessage)
              ErrorReply(exception, replyTo)
          }
          Behaviors.same

        case ReplyWithRepositories(repositories, replyTo) =>
          context.log.info(s"Parse repositories result received. Sending reply to sender")
          replyTo ! StatusReply.Success(Repositories(repositories))
          Behaviors.same

        case ErrorReply(throwable, replyTo) =>
          replyTo ! Error(throwable)
          Behaviors.same
      }
    }
  }
}
