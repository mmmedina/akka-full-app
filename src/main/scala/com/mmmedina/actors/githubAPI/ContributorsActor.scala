package com.mmmedina.actors.githubAPI

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.pattern.StatusReply
import akka.pattern.StatusReply.Error
import com.mmmedina.actors.githubAPI.RepositoriesActor.Repositories
import com.mmmedina.models.Contributor
import com.mmmedina.serialization.JsonFormats
import com.mmmedina.httpClient.GithubClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

object ContributorsActor extends JsonFormats with SprayJsonSupport {
  sealed trait Command

  final case class GetByRepo(
      organization: String,
      repositories: Repositories,
      replyTo: ActorRef[StatusReply[Contributors]]
  ) extends Command

  private final case class ErrorReply(error: Throwable, replyTo: ActorRef[StatusReply[Contributors]]) extends Command

  private final case class ReplyWithContributors(contributors: Seq[Contributor], replyTo: ActorRef[StatusReply[Contributors]])
      extends Command

  case class Contributors(contributors: Seq[Contributor])

  def apply(githubAPI: GithubClient): Behavior[Command] = {
    Behaviors.setup { context =>
      implicit val system: ActorSystem[Nothing] = context.system
      Behaviors.receiveMessage {
        case GetByRepo(organization, result, replyTo) =>
          context.log.info(s"Searching for contributors by organization name: $organization")
          val contributors = result.repositories.map {
            context.log.info(s"Start getting contributors by repository")
            repository => githubAPI.getContributorsByRepository(organization, repository.name)
          }
          context.log.info("finished requests to Github API. Sending response to get contributors")
          context.pipeToSelf(Future.sequence(contributors)) {
            case Success(contributors) =>
              ReplyWithContributors(contributors.flatten, replyTo)

            case Failure(exception) =>
              context.log.error(exception.getMessage)
              ErrorReply(exception, replyTo)
          }
          Behaviors.same

        case ReplyWithContributors(contributors, replyTo) =>
          context.log.info(s"Parse contributors result received. Sending reply to sender")
          replyTo ! StatusReply.Success(Contributors(contributors))
          Behaviors.same

        case ErrorReply(throwable, replyTo) =>
          replyTo ! Error(throwable)
          Behaviors.same
      }
    }
  }
}
