package com.mmmedina.actors.organization

import akka.pattern.StatusReply
import akka.pattern.StatusReply.Success
import com.mmmedina.TestService
import com.mmmedina.actors.OrganizationActor
import com.mmmedina.actors.OrganizationActor.ContributorsDTO
import com.mmmedina.models.ContributorDTO
import com.mmmedina.stubs.Stubs
import com.mmmedina.httpClient.GithubClient
import org.mockito.Mockito
import org.scalatestplus.mockito.MockitoSugar

class OrganizationActorSpec extends TestService with MockitoSugar with Stubs {
  val organizationName = "test"

  val mockClient: GithubClient = mock[GithubClient]
  "organization routes" should {
    "return OK response" when {
      "receives a valid organization name" in {
        // given
        val expectedResult: Seq[ContributorDTO] =
          Seq(
            ContributorDTO("another-contributor", 15),
            ContributorDTO("one-contributor", 22),
            ContributorDTO("other-contributor", 34)
          )

        Mockito.when(mockClient.getRepositories(organizationName)) thenReturn repositoriesResponse
        Mockito
          .when(mockClient.getContributorsByRepository(organizationName, "one-repository.js"))
          .thenReturn(contributorsResponseOneRepository)
        Mockito
          .when(mockClient.getContributorsByRepository(organizationName, "other-repository.js"))
          .thenReturn(contributorsResponseOtherRepository)

        // when
        val sender = testKit.spawn(OrganizationActor(mockClient), "organization")
        val probe  = testKit.createTestProbe[StatusReply[ContributorsDTO]]()
        sender ! OrganizationActor.GetContributors(organizationName, probe.ref)

        // then
        probe expectMessage Success(ContributorsDTO(expectedResult))
      }
    }
  }
}
