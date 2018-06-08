package provingground.interface

import provingground._
import translation._
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer

import scala.util.Try
import upickle.{Js, json}

import scala.util.Try
import scala.concurrent._

class MantleService(serverMode: Boolean){

  var keepAlive = true

  val baseRoute =
    get {
      path("resources" / Remaining) { path =>
        // println("serving from resource: " + path)
        getFromResource(path)
      }
    } ~ get {
      path("docs" / Remaining) { path =>
        // println("serving from resource: " + path)
        getFromFile(s"docs/$path")
      }
    } ~ path("halt") {
      if (!serverMode) {
        keepAlive = false
        complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Shutting down"))
      }
      else
        complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Server mode: cannot shut down"))
    }

  import MantleServer.executionContext

  val buildRoute =
    get {
      path("build") {
        val response: String =
          Try(Site.mkHome()).map {
            (_) =>
              Future(
                Try(Site.mkSite())
                  .getOrElse(pprint.log("Cannot build site, perhaps this is not run from the root of the repo"))
              )
              "Building site"
          }.getOrElse("Cannot build site, perhaps this is not run from the root of the repo")

        complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, response))
      }
    }

  val indexHTML =
    """
      |
      |  <ul>
      |   <li> <a href="build" target="_blank">Build</a> the web page.</li>
      |   <li> <a href="prover.html">Prover</a> experiments. </li>
      |   <li> <a href="scripts/index.html" target="_blank">Script Editor</a></li>
      |  </ul>
      |  <script type="text/javascript" src="resources/out.js"></script>
      |  <script>
      |   mantlemenu.add()
      |  </script>
      |
    """.stripMargin

  val proverHTML =
    """
      |
      |  <div id="prover-div"></div>
      |  <script type="text/javascript" src="resources/out.js"></script>
      |  <script>
      |   mantlemenu.add()
      |   prover.load()
      |  </script>
      |
    """.stripMargin

  val mantleRoute =
    get{
      (pathSingleSlash | path("index.html")) {
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
          Site.page(indexHTML, "resources/", "ProvingGround Server" , !serverMode)))
      }
    } ~ get{
      path("prover.html"){
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
          Site.page(proverHTML, "resources/", "Prover Experimentation" , !serverMode)))
      }
    } ~ post {
      path("monoid-proof"){
        pprint.log("seeking proof")
        val resultFut =
          MonoidServer.seekResultFut.map{
            (js) =>
              HttpEntity(ContentTypes.`application/json`, js.toString)
        }
        complete(resultFut)
      }
    }

   val route = baseRoute ~ buildRoute ~ mantleRoute
}


object MantleServer extends  App {
  implicit val system: ActorSystem = ActorSystem("provingground")
  implicit val materializer = ActorMaterializer()

  // needed for the future flatMap/onComplete in the end
  implicit val executionContext: scala.concurrent.ExecutionContextExecutor =
    system.dispatcher

  // import MantleService._

  import ammonite.ops._

  def path(s: String): Path = scala.util.Try(Path(s)).getOrElse(pwd / RelPath(s))

  implicit val pathRead: scopt.Read[Path] =
    scopt.Read.reads(path)

  case class Config(
                     scriptsDir: Path = pwd / "repl-scripts",
                     objectsDir: Path = pwd / "core" / "src" / "main" / "scala" / "provingground" / "scripts",
                     host: String = "localhost",
                     port: Int = 8080,
                    serverMode: Boolean = false)

  // val config = Config()

  val parser = new scopt.OptionParser[Config]("provingground-server") {
    head("ProvingGround Server", "0.1")

    opt[String]('i', "interface")
      .action((x, c) => c.copy(host = x))
      .text("server ip")
    opt[Int]('p', "port")
      .action((x, c) => c.copy(port = x))
      .text("server port")
    opt[Path]('s', "scripts")
      .action((x, c) => c.copy(scriptsDir = x))
      .text("scripts directory")
    opt[Path]('o', "objects")
      .action((x, c) => c.copy(objectsDir = x))
      .text("created objects directory")
    opt[Unit]("server")
    .action((x, c) => c.copy(serverMode = true))
    .text("running in server mode")
  }


  parser.parse(args, Config()) match {
    case Some(config) =>

      val ammServer = new AmmScriptServer(config.scriptsDir, config.objectsDir)

      val server = new MantleService(config.serverMode)


      val bindingFuture =
        Http().bindAndHandle(server.route ~ pathPrefix("scripts")(ammServer.route), config.host, config.port)

      val exitMessage =
        if (config.serverMode) "Kill process to exit"
        else "Exit by clicking Halt on the web page (or 'curl localhost:8080/halt' from the command line)"

      println(s"Server online at http://${config.host}:${config.port}/\n$exitMessage")

      while (server.keepAlive) {
        Thread.sleep(10)
      }

      println("starting shutdown")

      bindingFuture
        .flatMap(_.unbind()) // trigger unbinding from the port
        .onComplete(_ => system.terminate()) // and shutdown when done

    case None =>
      println("invalid options")
  }

}
