package com.mmmedina.actors

import akka.actor.typed._
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.pattern.StatusReply
import akka.pattern.StatusReply.Error
import akka.util.Timeout
import com.mmmedina.actors.githubAPI.ContributorsActor.Contributors
import com.mmmedina.actors.githubAPI.{ContributorsActor, RepositoriesActor}
import com.mmmedina.models.ContributorDTO
import com.mmmedina.serialization.JsonFormats
import com.mmmedina.httpClient.GithubClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object OrganizationActor extends JsonFormats with SprayJsonSupport {

  final case class ContributorsDTO(contributors: Seq[ContributorDTO])

  sealed trait Command

  final case class GetContributors(organizationName: String, replyTo: ActorRef[StatusReply[ContributorsDTO]]) extends Command

  private final case class GroupFilterAndSortContributors(
  contributors: Contributors,
  replyTo: ActorRef[StatusReply[ContributorsDTO]]
  ) extends Command

  private final case class ErrorHandler(error: Throwable, replyTo: ActorRef[StatusReply[ContributorsDTO]]) extends Command

  def apply(client: GithubClient): Behavior[Command] = Behaviors.setup { context =>
    implicit val system: ActorSystem[Nothing] = context.system
    implicit val timeout: Timeout             = Timeout(80.seconds)
    implicit val scheduler: Scheduler         = context.system.scheduler

    val repositoriesActor = context.spawn(RepositoriesActor(client), "repositories")
    val contributorsActor = context.spawn(ContributorsActor(client), "contributors")

    Behaviors.receiveMessage {
      case GetContributors(organizationName, replyTo) =>
        val contributors = for {
          repositories <- repositoriesActor.askWithStatus(RepositoriesActor.GetByOrganization(organizationName, _))
          contributors <-
            contributorsActor.askWithStatus(ContributorsActor.GetByRepo(organizationName, repositories, _))
        } yield contributors
        context.pipeToSelf(contributors) {
          case Success(contributorsList) =>
            context.log.info("Group filter and sort contributors")
            GroupFilterAndSortContributors(contributorsList, replyTo)
          case Failure(exception)        => ErrorHandler(exception, replyTo)
        }
        Behaviors.same

      case GroupFilterAndSortContributors(result, replyTo) =>
        val contributorsDTO = result.contributors
          .groupBy(_.login)
          .view
          .mapValues { group => group.map(_.contributions).sum }
          .map(mapValue => ContributorDTO(mapValue._1, mapValue._2))
          .toSeq
          .sortBy(_.contributions)
        context.log.info(s"Finished process OK. Number of contributors returned: ${contributorsDTO.length}")
        replyTo ! StatusReply.Success(ContributorsDTO(contributorsDTO))

        Behaviors.same

      case ErrorHandler(throwable, replyTo) =>
        replyTo ! Error(throwable)
        Behaviors.same
    }
  }
}
