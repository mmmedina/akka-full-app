package com.mmmedina.serialization

import com.mmmedina.actors.OrganizationActor.ContributorsDTO
import com.mmmedina.models.{Contributor, ContributorDTO, Repository}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

trait JsonFormats extends DefaultJsonProtocol {
  implicit val RepositoryFormat: RootJsonFormat[Repository]           = jsonFormat2(Repository)
  implicit val ContributorFormat: RootJsonFormat[Contributor]         = jsonFormat2(Contributor)
  implicit val ContributorDTOFormat: RootJsonFormat[ContributorDTO]   = jsonFormat2(ContributorDTO)
  implicit val ContributorsDTOFormat: RootJsonFormat[ContributorsDTO] = jsonFormat1(ContributorsDTO)
}
