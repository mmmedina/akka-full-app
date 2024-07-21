# Full Akka Application
Application that retrieves ordered contributors with their contributions
by organization name from the [Github API](https://docs.github.com/en/rest)
* [Stack](#stack)
* [How to run](#how-to-run)
* [Preconditions to execute request](#preconditions-to-execute-request)

---

>### Stack
- _Scala 2.13_
- _Akka HTTP_
- _Akka Actors_
- _Akka Streams_
- _Spray JSON_
- _Scalatest_
- _Logback_

>### How to run
```bash
1 sbt clean compile
2 sbt run
```
This will start the project on localhost:8083

To retrieve all contributors of an organization try a GET request to:
localhost:8083/org/*ORGANIZATION_NAME*/contributors

>### Preconditions to execute request
- A valid organization name
- Set your own valid Github Token in **GH_TOKEN** enviroment variable:
*export GH_TOKEN=ghp_XXXXXXXXXXXX*