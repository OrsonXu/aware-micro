package com.awareframework.micro

import com.mitchellbosecke.pebble.PebbleEngine
import io.github.oshai.kotlinlogging.KotlinLogging
import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.config.ConfigStoreOptions
import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.OpenOptions
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.net.PemKeyCertOptions
import io.vertx.core.net.PemTrustOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.ext.web.codec.BodyCodec
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.ext.web.templ.pebble.PebbleTemplateEngine
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory


import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class MainVerticle : AbstractVerticle() {

  private val logger = KotlinLogging.logger {}

  private lateinit var parameters: JsonObject
  private lateinit var httpServer: HttpServer

  override fun start(startPromise: Promise<Void>) {

    logger.info { "AWARE Micro initializing..." }

    val serverOptions = HttpServerOptions().setMaxWebSocketMessageSize(1024 * 1024 * 20).setMaxChunkSize(1024 * 1024 * 50).setMaxInitialLineLength(1024 * 1024 * 50).setMaxHeaderSize(1024 * 1024 * 50).setMaxFormAttributeSize(-1); 
    val pebbleEngine = PebbleTemplateEngine.create(vertx, PebbleEngine.Builder().cacheActive(false).build())
    val eventBus = vertx.eventBus()

    val router = Router.router(vertx)
    router.route().handler(BodyHandler.create().setBodyLimit(1024 * 1024 * 50));
    router.route("/cache/*").handler(StaticHandler.create("cache"))
    router.route().handler {
      logger.info { "Processing ${it.request().scheme()} ${it.request().method()} : ${it.request().path()} with the following data ${it.request().params().toList()}" }
      it.next()
    }

    val configJsonFile = ConfigStoreOptions()
      .setType("file")
      .setFormat("json")
      .setConfig(JsonObject().put("path", "./aware-config.json"))

    val configRetrieverOptions = ConfigRetrieverOptions()
      .setScanPeriod(5000)
      .addStore(configJsonFile)

    val configReader = ConfigRetriever.create(vertx, configRetrieverOptions)
    configReader.getConfig { config ->

      if (config.succeeded() && config.result().containsKey("server")) {
        parameters = config.result()
        val serverConfig = parameters.getJsonObject("server")
        val study = parameters.getJsonObject("study")

        // HttpServerOptions.host is the host to listen on. So using |server_host|, not |external_server_host| here.
        // See also: https://vertx.io/docs/4.3.3/apidocs/io/vertx/core/net/NetServerOptions.html#DEFAULT_HOST
        serverOptions.host = serverConfig.getString("server_host")


        /**
         * Uncomment this route for debug sake
         * Handles incoming HTTP requests, extracts and processes form-encoded data, 
         * and logs key request details before passing it to the next handler.
         * 
         * - Parses `application/x-www-form-urlencoded` data manually.
         * - Extracts the "data" field without URL-decoding JSON values.
         * - Parses JSON data if present and logs timestamps from the first and last records.
         * - Logs request method, headers, and parsed content.
         * - Calls `routingContext.next()` to continue request processing.
         */
      //   router.route().handler { routingContext ->
      //     val request = routingContext.request()
      //     val body = routingContext.body().asString() ?: "No Body"
      
      //     // Log the raw body to ensure it is received correctly
      //     logger.warn { "Raw Body Received: $body" }
      
      //     var dataString: String? = null
      
      //     // Manually parse x-www-form-urlencoded data
      //     val params = body.split("&")
      //     val paramMap = mutableMapOf<String, String>()
      
      //     for (param in params) {
      //         val parts = param.split("=")
      //         if (parts.size == 2) {
      //             val key = parts[0]
      //             val rawValue = parts[1]
      
      //             // Decode only if the key is not "data" (JSON should not be URL-decoded)
      //             val value = if (key == "data") rawValue else URLDecoder.decode(rawValue, StandardCharsets.UTF_8.name())
      
      //             paramMap[key] = value
      //         }
      //     }
      
      //     // Extract the "data" field
      //     dataString = paramMap["data"]
      //     logger.warn { "Extracted 'data' field: ${dataString ?: "N/A"}" }
      
      //     var dataLength = 0
      //     var firstTimestamp: Long? = null
      //     var lastTimestamp: Long? = null
      //     var firstRecord: JsonObject? = null
      //     var lastRecord: JsonObject? = null
      
      //     if (!dataString.isNullOrBlank()) {
      //         try {
      //             // Parse the "data" field as a JSON array
      //             val dataArray = JsonArray(dataString)
      //             dataLength = dataArray.size()
      
      //             if (dataLength > 0) {
      //                 firstRecord = dataArray.getJsonObject(0)
      //                 lastRecord = dataArray.getJsonObject(dataLength - 1)
      
      //                 firstTimestamp = firstRecord.getLong("timestamp")
      //                 lastTimestamp = lastRecord.getLong("timestamp")
      //             }
      //         } catch (e: Exception) {
      //             logger.warn(e) { "Failed to parse data JSON" }
      //         }
      //     }
      
      //     // Log request details
      //     logger.warn { 
      //         """
      //         HTTP Request Received
      //         Method: ${request.method()}
      //         Path: ${request.path()}
      //         Headers: ${request.headers().entries()}
      //         Content-Length (Header): ${request.getHeader("Content-Length") ?: "N/A"}
      //         Body Length (bytes): ${body.length}
      //         Data List Length: $dataLength
      //         First Timestamp: ${firstTimestamp ?: "N/A"}
      //         Last Timestamp: ${lastTimestamp ?: "N/A"}
      //         First Record: ${firstRecord?.encodePrettily() ?: "N/A"}
      //         Last Record: ${lastRecord?.encodePrettily() ?: "N/A"}
      //         ===========================
      //         """.trimIndent()
      //     }
      
      //     routingContext.next()
      // }
      
           
      fun parseFormEncodedBody(body: String): Map<String, String> {
        val paramMap = mutableMapOf<String, String>()
    
        // Split the body by '&' to get key-value pairs
        val params = body.split("&")
        
        for (param in params) {
            val parts = param.split("=")
            if (parts.size == 2) {
                val key = parts[0]
                val rawValue = parts[1]
    
                // Decode only if the key is not "data" (JSON should not be URL-decoded)
                val value = if (key == "data") rawValue else URLDecoder.decode(rawValue, StandardCharsets.UTF_8.name())
    
                paramMap[key] = value
            }
        }
    
        return paramMap
    }


        /**
         * Generate QRCode to join the study using Google's Chart API
         */
        router.route(HttpMethod.GET, "/:studyNumber/:studyKey").handler { route ->
          if (validRoute(
              study,
              route.request().getParam("studyNumber").toInt(),
              route.request().getParam("studyKey")
            )
          ) {
            vertx.fileSystem().delete("./cache/qrcode.png") {
              if (it.succeeded()) logger.info { "Cleared old qrcode..." }
            }
            vertx.fileSystem().open(
              "./cache/qrcode.png",
              OpenOptions().setTruncateExisting(true).setCreate(true).setWrite(true)
            ) { write ->
              if (write.succeeded()) {
                val asyncQrcode = write.result()
                val webClientOptions = WebClientOptions()
                  .setKeepAlive(true)
                  .setPipelining(true)
                  .setFollowRedirects(true)
                  .setSsl(true)
                  .setTrustAll(true)

                val client = WebClient.create(vertx, webClientOptions)
                val serverURL =
                  "${getExternalServerHost(serverConfig)}:${getExternalServerPort(serverConfig)}/index.php/${study.getInteger(
                    "study_number"
                  )}/${study.getString("study_key")}"

                logger.info { "URL encoded for the QRCode is: $serverURL" }

                client.get(
                  443, "qrcode.tec-it.com",
                  "/API/QRCode?size=small&data=$serverURL"
                )
                  .`as`(BodyCodec.pipe(asyncQrcode, true))
                  .send { request ->
                    if (request.succeeded()) {
                      pebbleEngine.render(JsonObject().put("studyURL", serverURL), "templates/qrcode.peb") { pebble ->
                        if (pebble.succeeded()) {
                          route.response().statusCode = 200
                          route.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/html").end(pebble.result())
                        }
                      }
                    } else {
                      logger.error(request.cause()) { "QRCode creation failed." }
                    }
                  }
              }
            }
          }
        }

        /**
         * This route is called:
         * - when joining the study, returns the JSON with all the settings from the study. Can be called from apps using Aware.joinStudy(URL) or client's QRCode scanner
         * - when checking study status with the study_check=1.
         */
        router.route(HttpMethod.POST, "/index.php/:studyNumber/:studyKey").handler { route ->
          if (validRoute(
              study,
              route.request().getParam("studyNumber").toInt(),
              route.request().getParam("studyKey")
            )
          ) {
            if (route.request().getFormAttribute("study_check") == "1") {
              val status = JsonObject()
              status.put("status", study.getBoolean("study_active"))
              status.put(
                "config",
                "[]"
              ) //NOTE: if we send the configuration, it will keep reapplying the settings on legacy clients. Sending empty JsonArray (i.e., no changes)
              route.response().end(JsonArray().add(status).encode())
              route.next()
            } else {
              logger.info { "Study configuration: ${getStudyConfig().encodePrettily()}" }
              route.response().end(getStudyConfig().encode())
            }
          } else {
            route.response().statusCode = 401
            route.response().end()
          }
        }

        /**
         * Legacy: this will be hit by legacy client to retrieve the study information. It retuns JsonObject with (defined also in aware-config.json on AWARE Micro):
        {
        "study_key" : "studyKey",
        "study_number" : 1,
        "study_name" : "AWARE Micro demo study",
        "study_description" : "This is a demo study to test AWARE Micro",
        "researcher_first" : "First Name",
        "researcher_last" : "Last Name",
        "researcher_contact" : "your@email.com"
        }
         */
        router.route(HttpMethod.GET, "/index.php/webservice/client_get_study_info/:studyKey").handler { route ->
          if (route.request().getParam("studyKey") == study.getString("study_key")) {
            route.response().end(study.encode())
          } else {
            route.response().statusCode = 401
            route.response().end()
          }
        }

        router.route(HttpMethod.POST, "/index.php/:studyNumber/:studyKey/:table/:operation").handler { route ->

        val body = route.body().asString() ?: ""
        val parsedParams = parseFormEncodedBody(body) // Parse the form data into a map
          
          if (validRoute(
              study,
              route.request().getParam("studyNumber").toInt(),
              route.request().getParam("studyKey")
            )
          ) {
            when (route.request().getParam("operation")) {
              "create_table" -> {
                //Commented the following line as we merged with insert. Only here so that legacy client thinks all is ok
                //eventBus.publish("createTable", route.request().getParam("table"))
                route.response().statusCode = 200
                route.response().end()
              }
              "insert" -> {
                eventBus.publish(
                  "insertData",
                  JsonObject()
                    .put("table", route.request().getParam("table"))
                    .put("device_id", parsedParams["device_id"]) // Extracted from the parsed body
                    .put("data", parsedParams["data"]) // Extracted from the parsed body
                )
                route.response().statusCode = 200
                route.response().end()
              }
              "update" -> {
                eventBus.publish(
                  "updateData",
                  JsonObject()
                    .put("table", route.request().getParam("table"))
                    .put("device_id", parsedParams["device_id"]) // Extracted from the parsed body
                    .put("data", parsedParams["data"]) // Extracted from the parsed body
                )
                route.response().statusCode = 200
                route.response().end()
              }
              "delete" -> {
                eventBus.publish(
                  "deleteData",
                  JsonObject()
                    .put("table", route.request().getParam("table"))
                    .put("device_id", parsedParams["device_id"]) // Extracted from the parsed body
                    .put("data", parsedParams["data"]) // Extracted from the parsed body
                )
                route.response().statusCode = 200
                route.response().end()
              }
              "query" -> {
                val requestData = JsonObject()
                  .put("table", route.request().getParam("table"))
                  .put("device_id", parsedParams["device_id"]) 
                  .put("start", route.request().getFormAttribute("start").toDouble())
                  .put("end", route.request().getFormAttribute("end").toDouble())

                eventBus.request<JsonArray>("getData", requestData) { response ->
                  if (response.succeeded()) {
                    route.response().statusCode = 200
                    route.response().end(response.result().body().encode())
                  } else {
                    route.response().statusCode = 401
                    route.response().end()
                  }
                }
              }
              else -> {
                route.response().statusCode = 401
                route.response().end()
              }
            }
          } else {
            route.response().statusCode = 401
            route.response().end()
          }
        }

        /**
         * Default route, landing page of the server
         */
        router.route(HttpMethod.GET, "/").handler { route ->
          route.response().putHeader("content-type", "text/html").end(
            "Hello from AWARE Micro!<br/>Join study: <a href=\"${getExternalServerHost(serverConfig)}:${getExternalServerPort(serverConfig)}/${study.getInteger(
              "study_number"
            )}/${study.getString("study_key")}\">HERE</a>"
          )
        }

        //Use SSL
        if (serverConfig.getString("path_fullchain_pem").isNotEmpty() && serverConfig.getString("path_key_pem").isNotEmpty()) {
          serverOptions.pemTrustOptions = PemTrustOptions().addCertPath(serverConfig.getString("path_fullchain_pem"))
          serverOptions.pemKeyCertOptions = PemKeyCertOptions()
            .setCertPath(serverConfig.getString("path_fullchain_pem"))
            .setKeyPath(serverConfig.getString("path_key_pem"))
          serverOptions.isSsl = true
        }

        httpServer = vertx.createHttpServer(serverOptions)
          .requestHandler(router)
          .listen(serverConfig.getInteger("server_port")) { server ->
            if (server.succeeded()) {
              when (serverConfig.getString("database_engine")) {
                "mysql" -> {
                  vertx.deployVerticle("com.awareframework.micro.MySQLVerticle")
                }
                "postgres" -> {
                  vertx.deployVerticle("com.awareframework.micro.PostgresVerticle")
                }
                else -> {
                  logger.info { "Not storing data into a database engine: mysql, postgres" }
                }
              }

              vertx.deployVerticle("com.awareframework.micro.WebsocketVerticle")

              logger.info { "AWARE Micro API at ${getExternalServerHost(serverConfig)}:${getExternalServerPort(serverConfig)}" }
              logger.info { "Serving study config: ${getStudyConfig()}" }
              startPromise.complete()
            } else {
              logger.error(server.cause()) { "AWARE Micro initialisation failed!" }
              startPromise.fail(server.cause())
            }
          }

        configReader.listen { change ->
          val newConfig = change.newConfiguration
          httpServer.close()

          val newServerConfig = newConfig.getJsonObject("server")
          val newServerOptions = HttpServerOptions()

          if (newServerConfig.getString("path_fullchain_pem").isNotEmpty() && newServerConfig.getString("path_key_pem").isNotEmpty()) {
            newServerOptions.pemTrustOptions =
              PemTrustOptions().addCertPath(newServerConfig.getString("path_fullchain_pem"))

            newServerOptions.pemKeyCertOptions = PemKeyCertOptions()
              .setCertPath(newServerConfig.getString("path_fullchain_pem"))
              .setKeyPath(newServerConfig.getString("path_key_pem"))
            newServerOptions.isSsl = true
          }

          httpServer = vertx.createHttpServer(newServerOptions)
            .requestHandler(router)
            .listen(newServerConfig.getInteger("server_port")) { server ->
              if (server.succeeded()) {
                when (newServerConfig.getString("database_engine")) {
                  "mysql" -> {
                    vertx.undeploy("com.awareframework.micro.MySQLVerticle")
                    vertx.deployVerticle("com.awareframework.micro.MySQLVerticle")
                  }
                  "postgres" -> {
                    vertx.undeploy("com.awareframework.micro.PostgresVerticle")
                    vertx.deployVerticle("com.awareframework.micro.PostgresVerticle")
                  }
                  else -> {
                    logger.info { "Not storing data into a database engine: mysql, postgres" }
                  }
                }

                vertx.undeploy("com.awareframework.micro.WebsocketVerticle")
                vertx.deployVerticle("com.awareframework.micro.WebsocketVerticle")

                logger.info { "AWARE Micro API at ${getExternalServerHost(newServerConfig)}:${getExternalServerPort(newServerConfig)}" }

              } else {
                logger.error(server.cause()) { "AWARE Micro initialisation failed!" }
              }
            }
        }

      } else { //this is a fresh instance, no server created yet.

        val configFile = JsonObject()

        //infrastructure info
        val server = JsonObject()
        server.put("database_engine", "mysql") //[mysql, postgres]
        server.put("database_host", "localhost")
        server.put("database_name", "studyDatabase")
        server.put("database_user", "databaseUser")
        server.put("database_pwd", "databasePassword")
        server.put("database_port", 3306)
        server.put("server_host", "http://localhost")
        server.put("server_port", 8080)
        server.put("websocket_port", 8081)
        server.put("path_fullchain_pem", "")
        server.put("path_key_pem", "")
        configFile.put("server", server)

        //study info
        val study = JsonObject()
        study.put("study_key", "4lph4num3ric")
        study.put("study_number", 1)
        study.put("study_name", "AWARE Micro demo study")
        study.put("study_active", true)
        study.put("study_start", System.currentTimeMillis())
        study.put("study_description", "This is a demo study to test AWARE Micro")
        study.put("researcher_first", "First Name")
        study.put("researcher_last", "Last Name")
        study.put("researcher_contact", "your@email.com")
        configFile.put("study", study)

        //AWARE framework settings from both sensors and plugins
        val sensors =
          getSensors("https://raw.githubusercontent.com/denzilferreira/aware-client/master/aware-core/src/main/res/xml/aware_preferences.xml")

        configFile.put("sensors", sensors)

        val pluginsList = HashMap<String, String>()
        pluginsList["com.aware.plugin.ambient_noise"] =
          "https://raw.githubusercontent.com/denzilferreira/com.aware.plugin.ambient_noise/master/com.aware.plugin.ambient_noise/src/main/res/xml/preferences_ambient_noise.xml"
        pluginsList["com.aware.plugin.contacts_list"] =
          "https://raw.githubusercontent.com/denzilferreira/com.aware.plugin.contacts_list/master/com.aware.plugin.contacts_list/src/main/res/xml/preferences_contacts_list.xml"
        pluginsList["com.aware.plugin.device_usage"] =
          "https://raw.githubusercontent.com/denzilferreira/com.aware.plugin.device_usage/master/com.aware.plugin.device_usage/src/main/res/xml/preferences_device_usage.xml"
        pluginsList["com.aware.plugin.esm.scheduler"] =
          "https://raw.githubusercontent.com/denzilferreira/com.aware.plugin.esm.scheduler/master/com.aware.plugin.esm.scheduler/src/main/res/xml/preferences_esm_scheduler.xml"
        pluginsList["com.aware.plugin.fitbit"] =
          "https://raw.githubusercontent.com/denzilferreira/com.aware.plugin.fitbit/master/com.aware.plugin.fitbit/src/main/res/xml/preferences_fitbit.xml"
        pluginsList["com.aware.plugin.google.activity_recognition"] =
          "https://raw.githubusercontent.com/denzilferreira/com.aware.plugin.google.activity_recognition/master/com.aware.plugin.google.activity_recognition/src/main/res/xml/preferences_activity_recog.xml"
        pluginsList["com.aware.plugin.google.auth"] =
          "https://raw.githubusercontent.com/denzilferreira/com.aware.plugin.google.auth/master/com.aware.plugin.google.auth/src/main/res/xml/preferences_google_auth.xml"
        pluginsList["com.aware.plugin.google.fused_location"] =
          "https://raw.githubusercontent.com/denzilferreira/com.aware.plugin.google.fused_location/master/com.aware.plugin.google.fused_location/src/main/res/xml/preferences_fused_location.xml"
        pluginsList["com.aware.plugin.openweather"] =
          "https://raw.githubusercontent.com/denzilferreira/com.aware.plugin.openweather/master/com.aware.plugin.openweather/src/main/res/xml/preferences_openweather.xml"
        pluginsList["com.aware.plugin.sensortag"] =
          "https://raw.githubusercontent.com/denzilferreira/com.aware.plugin.sensortag/master/com.aware.plugin.sensortag/src/main/res/xml/preferences_sensortag.xml"
        pluginsList["com.aware.plugin.sentimental"] =
          "https://raw.githubusercontent.com/denzilferreira/com.aware.plugin.sentimental/master/com.aware.plugin.sentimental/src/main/res/xml/preferences_sentimental.xml"
        pluginsList["com.aware.plugin.studentlife.audio_final"] =
          "https://raw.githubusercontent.com/denzilferreira/com.aware.plugin.studentlife.audio_final/master/com.aware.plugin.studentlife.audio/src/main/res/xml/preferences_conversations.xml"

        val plugins = getPlugins(pluginsList)
        configFile.put("plugins", plugins)

        vertx.fileSystem().writeFile("./aware-config.json", Buffer.buffer(configFile.encodePrettily())) { result ->
          if (result.succeeded()) {
            logger.info { "You can now configure your server by editing the aware-config.json that was automatically created. You can now stop this instance (press Ctrl+C)" }
          } else {
            logger.error(result.cause()) { "Failed to create aware-config.json." }
          }
        }
      }
    }
  }

  /**
   * Check valid study key and number
   */
  fun validRoute(studyInfo: JsonObject, studyNumber: Int, studyKey: String): Boolean {
    return studyNumber == studyInfo.getInteger("study_number") && studyKey == studyInfo.getString("study_key")
  }

  fun getStudyConfig(): JsonArray {
    val serverConfig = parameters.getJsonObject("server")
    //println("Server config: ${serverConfig.encodePrettily()}")

    val study = parameters.getJsonObject("study")
    //println("Study info: ${study.encodePrettily()}")

    val sensors = JsonArray()
    val plugins = JsonArray()

    val awareSensors = parameters.getJsonArray("sensors")
    for (i in 0 until awareSensors.size()) {
      val awareSensor = awareSensors.getJsonObject(i)
      val sensorSettings = awareSensor.getJsonArray("settings")
      for (j in 0 until sensorSettings.size()) {
        val setting = sensorSettings.getJsonObject(j)

        val awareSetting = JsonObject()
        awareSetting.put("setting", setting.getString("setting"))

        when (setting.getString("setting")) {
          "status_webservice" -> awareSetting.put("value", "true")
          "webservice_server" -> awareSetting.put(
            "value",
            "${getExternalServerHost(serverConfig)}:${getExternalServerPort(serverConfig)}/index.php/${study.getInteger(
              "study_number"
            )}/${study.getString("study_key")}"
          )
          else -> awareSetting.put("value", setting.getString("defaultValue"))
        }
        sensors.add(awareSetting)
      }
    }

    var awareSetting = JsonObject()
    awareSetting.put("setting", "study_id")
    awareSetting.put("value", study.getString("study_key"))
    sensors.add(awareSetting)

    awareSetting = JsonObject()
    awareSetting.put("setting", "study_start")
    awareSetting.put("value", study.getDouble("study_start"))
    sensors.add(awareSetting)

    val awarePlugins = parameters.getJsonArray("plugins")
    for (i in 0 until awarePlugins.size()) {
      val awarePlugin = awarePlugins.getJsonObject(i)
      val pluginSettings = awarePlugin.getJsonArray("settings")

      val pluginOutput = JsonObject()
      pluginOutput.put("plugin", awarePlugin.getString("package_name"))

      val pluginSettingsOutput = JsonArray()
      for (j in 0 until pluginSettings.size()) {
        val setting = pluginSettings.getJsonObject(j)
        val settingOutput = JsonObject()
        settingOutput.put("setting", setting.getString("setting"))
        settingOutput.put("value", setting.getString("defaultValue"))
        pluginSettingsOutput.add(settingOutput)
      }
      pluginOutput.put("settings", pluginSettingsOutput)

      plugins.add(pluginOutput)
    }

    val schedulers = parameters.getJsonArray("schedulers")

    val output = JsonArray()
    output.add(JsonObject().put("sensors", sensors).put("plugins", plugins))
    if (schedulers != null) {
      output.getJsonObject(0).put("schedulers", schedulers)
    }
    return output
  }

  /**
   * This parses the aware-client xml file to retrieve all possible settings for a study
   */
  fun getSensors(xmlUrl: String): JsonArray {
    val sensors = JsonArray()
    val awarePreferences = URL(xmlUrl).openStream()

    val docFactory = DocumentBuilderFactory.newInstance()
    val docBuilder = docFactory.newDocumentBuilder()
    val doc = docBuilder.parse(awarePreferences)
    val docRoot = doc.getElementsByTagName("PreferenceScreen")

    for (i in 1..docRoot.length) {
      val child = docRoot.item(i)
      if (child != null) {

        val sensor = JsonObject()
        if (child.attributes.getNamedItem("android:key") != null)
          sensor.put("sensor", child.attributes.getNamedItem("android:key").nodeValue)
        if (child.attributes.getNamedItem("android:title") != null)
          sensor.put("title", child.attributes.getNamedItem("android:title").nodeValue)
        if (child.attributes.getNamedItem("android:icon") != null)
          sensor.put("icon", getSensorIcon(child.attributes.getNamedItem("android:icon").nodeValue))
        if (child.attributes.getNamedItem("android:summary") != null)
          sensor.put("summary", child.attributes.getNamedItem("android:summary").nodeValue)

        val settings = JsonArray()
        val subChildren = child.childNodes
        for (j in 0..subChildren.length) {
          val subChild = subChildren.item(j)
          if (subChild != null && subChild.nodeName.contains("Preference")) {
            val setting = JsonObject()
            if (subChild.attributes.getNamedItem("android:key") != null)
              setting.put("setting", subChild.attributes.getNamedItem("android:key").nodeValue)
            if (subChild.attributes.getNamedItem("android:title") != null)
              setting.put("title", subChild.attributes.getNamedItem("android:title").nodeValue)
            if (subChild.attributes.getNamedItem("android:defaultValue") != null)
              setting.put("defaultValue", subChild.attributes.getNamedItem("android:defaultValue").nodeValue)
            if (subChild.attributes.getNamedItem("android:summary") != null && subChild.attributes.getNamedItem("android:summary").nodeValue != "%s")
              setting.put("summary", subChild.attributes.getNamedItem("android:summary").nodeValue)

            if (setting.containsKey("defaultValue"))
              settings.add(setting)
          }
        }
        sensor.put("settings", settings)
        sensors.add(sensor)
      }
    }
    return sensors
  }

  /**
   * This retrieves asynchronously the icons for each sensor from the client source code
   */
  private fun getSensorIcon(drawableId: String): String {
    val icon = drawableId.substring(drawableId.indexOf('/') + 1)
    val downloadUrl = "/denzilferreira/aware-client/raw/master/aware-core/src/main/res/drawable/*.png"

    vertx.fileSystem().mkdir("./cache") { cacheFolder ->
      if (cacheFolder.succeeded()) {
        logger.info { "Created cache folder" }
      }
    }

    vertx.fileSystem().exists("./cache/$icon.png") { iconResult ->
      if (!iconResult.result()) {
        vertx.fileSystem().open("./cache/$icon.png", OpenOptions().setCreate(true).setWrite(true)) { writeFile ->
          if (writeFile.succeeded()) {

            logger.info { "Downloading $icon.png" }

            val asyncFile = writeFile.result()
            val webClientOptions = WebClientOptions()
              .setKeepAlive(true)
              .setPipelining(true)
              .setFollowRedirects(true)
              .setSsl(true)
              .setTrustAll(true)

            val client = WebClient.create(vertx, webClientOptions)
            client.get(443, "github.com", downloadUrl.replace("*", icon))
              .`as`(BodyCodec.pipe(asyncFile, true))
              .send { request ->
                if (request.succeeded()) {
                  val iconFile = request.result()
                  logger.info { "Cached $icon.png: ${iconFile.statusCode() == 200}" }
                }
              }
          } else {
            logger.error(writeFile.cause()) { "Unable to create file." }
          }
        }
      }
    }

    return "$icon.png"
  }

  /**
   * This parses a list of plugins' xml to retrieve plugins' settings
   */
  private fun getPlugins(xmlUrls: HashMap<String, String>): JsonArray {
    val plugins = JsonArray()

    for (pluginUrl in xmlUrls) {
      val pluginPreferences = URL(pluginUrl.value).openStream()

      val docFactory = DocumentBuilderFactory.newInstance()
      val docBuilder = docFactory.newDocumentBuilder()
      val doc = docBuilder.parse(pluginPreferences)
      val docRoot = doc.getElementsByTagName("PreferenceScreen")

      for (i in 0..docRoot.length) {
        val child = docRoot.item(i)
        if (child != null) {

          val plugin = JsonObject()
          plugin.put("package_name", pluginUrl.key)

          if (child.attributes.getNamedItem("android:key") != null)
            plugin.put("plugin", child.attributes.getNamedItem("android:key").nodeValue)
          if (child.attributes.getNamedItem("android:icon") != null)
            plugin.put("icon", child.attributes.getNamedItem("android:icon").nodeValue)
          if (child.attributes.getNamedItem("android:summary") != null)
            plugin.put("summary", child.attributes.getNamedItem("android:summary").nodeValue)

          val settings = JsonArray()
          val subChildren = child.childNodes
          for (j in 0..subChildren.length) {
            val subChild = subChildren.item(j)
            if (subChild != null && subChild.nodeName.contains("Preference")) {
              val setting = JsonObject()
              if (subChild.attributes.getNamedItem("android:key") != null)
                setting.put("setting", subChild.attributes.getNamedItem("android:key").nodeValue)
              if (subChild.attributes.getNamedItem("android:title") != null)
                setting.put("title", subChild.attributes.getNamedItem("android:title").nodeValue)
              if (subChild.attributes.getNamedItem("android:defaultValue") != null)
                setting.put("defaultValue", subChild.attributes.getNamedItem("android:defaultValue").nodeValue)
              if (subChild.attributes.getNamedItem("android:summary") != null && subChild.attributes.getNamedItem("android:summary").nodeValue != "%s")
                setting.put("summary", subChild.attributes.getNamedItem("android:summary").nodeValue)

              if (setting.containsKey("defaultValue"))
                settings.add(setting)
            }
          }
          plugin.put("settings", settings)
          plugins.add(plugin)
        }
      }
    }
    return plugins
  }

  private fun getExternalServerHost(serverConfig: JsonObject): String {
    if (serverConfig.containsKey("external_server_host")) {
      return serverConfig.getString("external_server_host")
    }
    return serverConfig.getString("server_host")
  }

  private fun getExternalServerPort(serverConfig: JsonObject): Int {
    if (serverConfig.containsKey("external_server_port")) {
      return serverConfig.getInteger("external_server_port")
    }
    return serverConfig.getInteger("server_port")
  }
}
