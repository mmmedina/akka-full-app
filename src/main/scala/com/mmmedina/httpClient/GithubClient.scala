package com.mmmedina.httpClient

import akka.actor.typed.ActorSystem
import akka.http.javadsl.model.StatusCodes
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.headers.{Link, RawHeader}
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.{ActorAttributes, Supervision}
import akka.stream.scaladsl.{Sink, Source}
import com.mmmedina.models.{Contributor, Repository}
import com.mmmedina.serialization.JsonFormats
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class GithubClient(implicit system: ActorSystem[Nothing]) extends JsonFormats with SprayJsonSupport {

  val githubToken: String = system.settings.config.getString("full-akka-app.github.token")
  def http: HttpExt       = Http() // overridable method
  val log: Logger         = LoggerFactory.getLogger("c.s.utils.GithubClient$")

  def getRepositories(organizationName: String): Future[Seq[Repository]] = {
    val uri     = s"https://api.github.com/orgs/$organizationName/repos"
    val request = HttpRequest(uri = uri)
      .withHeaders(RawHeader("Authorization", s"Token $githubToken"))

    val testSupervisionDecider: Supervision.Decider = {
      case ex: InterruptedException =>
        log.warn(s"Exception INTERRUPTED run time exception ${ex.getMessage}")
        Supervision.Stop
      case ex: NoSuchElementException =>
        log.warn("Exception NO SUCH ELEMENT occurred and stopping stream", ex)
        Supervision.Stop
      case ex: UnsupportedOperationException =>
        log.warn("Exception UNSOPORTED occurred and stopping stream", ex)
        Supervision.Stop
      case ex: RuntimeException =>
        log.warn("Exception RUN TIME occurred and stopping stream", ex)
        Supervision.Stop
    }

    Source
      .unfoldAsync[Option[HttpRequest], HttpResponse](Some(request))(chainRequests)
      .map {
        case HttpResponse(StatusCodes.OK, _, entity, _) =>
        Unmarshal(entity).to[Seq[Repository]]
        case response                                   =>
        responseHandler(response)
      }
      .withAttributes(ActorAttributes.supervisionStrategy(testSupervisionDecider))
      .runWith(Sink.seq)
      .flatMap(value => Future.sequence(value).map(_.flatten))
  }

  def getContributorsByRepository(organizationName: String, repository: String): Future[Seq[Contributor]] = {
    val uri = s"/repos/$organizationName/$repository/contributors"
    Source
      .single(
        HttpRequest(uri = uri)
          .withHeaders(RawHeader("Authorization", s"Token $githubToken"))
      )
      .via(Http().outgoingConnectionHttps("api.github.com"))
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

  private def nextUri(r: HttpResponse): Seq[Uri] = for {
    linkHeader <- r.header[Link].toSeq
    value      <- linkHeader.values
    params     <- value.params if params.key == "rel" && params.value() == "next"
    _           = log.info(s"Requested page ${value.uri}")
  } yield value.uri

  private def getNextRequest(r: HttpResponse): Option[HttpRequest] =
    nextUri(r).headOption.map(next => HttpRequest(HttpMethods.GET, next))

  private def convertToStrict(r: HttpResponse): Future[HttpResponse] = {
    r.entity.toStrict(60.seconds).map(e => r.withEntity(e)) // TODO put this into conf file
  }

  private def chainRequests(reqOption: Option[HttpRequest]): Future[Option[(Option[HttpRequest], HttpResponse)]] =
    reqOption match {
      case Some(req) =>
        Http().singleRequest(req).flatMap { response =>
          if (response.status.isFailure()) Future.successful(Some(None -> response))
          else
            convertToStrict(response).map { strictResponse =>
              getNextRequest(strictResponse) match {
                case None => Some(None -> strictResponse)
                case next => Some(next -> strictResponse)
              }
            }
        }
      case None      => Future.successful(None)
    }
}
