package com.mmmedina.httpClient

import akka.actor.typed.ActorSystem
import akka.http.javadsl.model.StatusCodes
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.headers.{Link, RawHeader}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.{ActorAttributes, Supervision}
import akka.stream.scaladsl.{Sink, Source}
import com.mmmedina.models.{Contributor, Repository}
import com.mmmedina.serialization.JsonFormats
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class GithubClient(implicit system: ActorSystem[Nothing]) extends JsonFormats with SprayJsonSupport {

  val githubToken: String = system.settings.config.getString("full-akka-app.github.token")
  def http: HttpExt       = Http() // overridable method
  val log: Logger         = LoggerFactory.getLogger("c.s.utils.GithubClient$")

  private val testSupervisionDecider: Supervision.Decider = {
    case ex: InterruptedException          =>
      log.warn(s"Exception INTERRUPTED run time exception ${ex.getMessage}")
      Supervision.Stop
    case ex: NoSuchElementException        =>
      log.warn("Exception NO SUCH ELEMENT occurred and stopping stream", ex)
      Supervision.Stop
    case ex: UnsupportedOperationException =>
      log.warn("Exception UNSOPORTED occurred and stopping stream", ex)
      Supervision.Stop
    case ex: RuntimeException              =>
      log.warn("Exception RUN TIME occurred and stopping stream", ex)
      Supervision.Stop
  }

  def getRepositories(organizationName: String): Future[Seq[Repository]] = {
    val uri     = s"https://api.github.com/orgs/$organizationName/repos"
    val request = HttpRequest(uri = uri)
      .withHeaders(RawHeader("Authorization", s"Token $githubToken"))

    Http().singleRequest(request).flatMap { response =>
      if (response.status.isFailure()) {
        responseHandler(response)
      } else {
        getNextRequestValue(response) match {
          case None              =>
            response match {
              case HttpResponse(StatusCodes.OK, _, entity, _)    =>
                Unmarshal(entity).to[Seq[Repository]]
              case HttpResponse(StatusCodes.NO_CONTENT, _, _, _) =>
                log.warn("No content response received")
                Future(Seq[Repository]())
              case response                                      =>
                responseHandler(response)
            }
          case Some(requestData) =>
            response.discardEntityBytes()
            val a = for (count <- Seq.range(1, requestData._1.toInt + 1)) yield {
              val uri = s"/organizations/${requestData._2}/repos?page=$count"
              log.info(s"requestedPage: $uri")
              repositoriesHttpRequest(uri)
            }
            Future.sequence(a).map(_.flatten)
        }
      }
    }
  }

  def getContributorsByRepository(organizationName: String, repository: String): Future[Seq[Contributor]] = {
    val uri = s"/repos/$organizationName/$repository/contributors"
    commonGithubSource(uri)
      .withAttributes(ActorAttributes.supervisionStrategy(testSupervisionDecider))
      .map {
        case HttpResponse(StatusCodes.OK, _, entity, _)    =>
          Unmarshal(entity).to[Seq[Contributor]]
        case HttpResponse(StatusCodes.NO_CONTENT, _, _, _) =>
          log.warn("No content response received")
          Future(Seq[Contributor]())
        case response                                      =>
          responseHandler(response)
      }
      .runWith(Sink.head)
      .flatten
  }

  /////////////////////////// private util methods ///////////////////////////

  def repositoriesHttpRequest(uri: String): Future[Seq[Repository]] = {
    commonGithubSource(uri)
      .withAttributes(ActorAttributes.supervisionStrategy(testSupervisionDecider))
      .map {
        case HttpResponse(StatusCodes.OK, _, entity, _)    =>
          Unmarshal(entity).to[Seq[Repository]]
        case HttpResponse(StatusCodes.NO_CONTENT, _, _, _) =>
          log.warn("No content response received")
          Future(Seq[Repository]())
        case response                                      =>
          responseHandler(response)
      }
      .runWith(Sink.seq)
      .flatMap(value => Future.sequence(value).map(_.flatten))
  }

  private def responseHandler(response: HttpResponse) = response match {
    case HttpResponse(StatusCodes.NOT_FOUND, _, _, _)    =>
      val message = s"Contributor cannot be found"
      log.warn(s"$message. Status code: ${StatusCodes.NOT_FOUND}")
      throw new NoSuchElementException(message)
    case HttpResponse(StatusCodes.FORBIDDEN, _, _, _)    =>
      val message = s"Forbidden access error"
      log.warn(s"$message. Status code: ${StatusCodes.FORBIDDEN}")
      throw new InterruptedException(message)
    case HttpResponse(StatusCodes.UNAUTHORIZED, _, _, _) =>
      val message = s"Error trying to access the resource"
      log.warn(s"$message. Status code: ${StatusCodes.UNAUTHORIZED}")
      throw new UnsupportedOperationException(message)
    case HttpResponse(code, _, _, _)                     =>
      val message = s"Unexpected Error"
      log.warn(s"$message. Status code: $code")
      throw new RuntimeException(message)
  }

  private def getNextRequestValue(response: HttpResponse) = {
    val organizationData = for {
      linkHeader     <- response.header[Link].toSeq
      lastPageUri    <- linkHeader.values.tail
      lastPageNumber <- ("(?<==)[0-9]+".r findAllIn lastPageUri.uri.toString()).toList
      organizationId <- ("""(?<=/)[0-9]+""".r findAllIn lastPageUri.uri.toString()).toList
      _               = log.info(s"Last Page should be 5 $lastPageNumber")
      _               = log.info(s"Last Page should be ID $organizationId")
    } yield (lastPageNumber, organizationId)
    organizationData.headOption
  }

  private def commonGithubSource(uri: String) = {
    Source
      .single(
        HttpRequest(uri = uri)
          .withHeaders(RawHeader("Authorization", s"Token $githubToken"))
      )
      .via(Http().outgoingConnectionHttps("api.github.com"))
  }
}
