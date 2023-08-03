package com.mmmedina.stubs

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.util.FastFuture
import com.mmmedina.models.{Contributor, Repository}

import scala.concurrent.Future

trait Stubs {
  val repositoriesResponse: Future[Seq[Repository]]                 = FastFuture.successful {
    Seq(
      Repository(id = 95034939, name = "one-repository.js"),
      Repository(id = 95034940, name = "other-repository.js")
    )
  }
  val contributorsResponseOneRepository: Future[Seq[Contributor]]   = FastFuture.successful {
    Seq(
      Contributor(login = "one-contributor", contributions = 22),
      Contributor(login = "other-contributor", contributions = 31)
    )
  }
  val contributorsResponseOtherRepository: Future[Seq[Contributor]] = FastFuture.successful {
    Seq(
      Contributor(login = "another-contributor", contributions = 15),
      Contributor(login = "other-contributor", contributions = 3)
    )
  }
}
