package co.zzyun.wsocks.web

import io.vertx.core.AbstractVerticle
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import java.nio.charset.Charset
import java.util.*

class WebTest : AbstractVerticle() {
  companion object {
    private val base64Decoder = Base64.getDecoder()
    private fun base64Decode(string: String): ByteArray {
      return base64Decoder.decode(string.replace(".", "+")
        .replace("_", "/")
        .replace("-", "="))
    }
  }

  private val httpServerPort by lazy { config().getInteger("http_port") }
  private val logger = LoggerFactory.getLogger(this::class.java)

  //private val webClient by lazy { WebClient.create(vertx) }
  private val httpClient by lazy { vertx.createHttpClient(HttpClientOptions().setKeepAlive(false)) }
  private val payload by lazy {
    File(config().getString("payload")).readText(Charset.defaultCharset())
  }
  private val index by lazy {
    File(config().getString("index")).readText(Charset.defaultCharset())
  }

  override fun start() {
    super.start()
    vertx.createHttpServer().requestHandler {

      try {
        when (it.path()) {
          "/proxy" -> {
            handleRequest(it)
          }
          "/index" -> {
            it.response().end(index)
          }
          else -> {
            throw FileNotFoundException("Path not found");
          }
        }
      } catch (e:FileNotFoundException){
        logger.error("Path not found: ${it.path()}")
        it.response().setStatusCode(500).end(e.message)
      } catch (e: Throwable) {
        it.response().setStatusCode(500).end(e.message)
        e.printStackTrace()
      }
    }.listen(httpServerPort) {
      logger.info("Listen at $httpServerPort")
    }
  }

  private fun handleRequest(req: HttpServerRequest) {
    val url = String(base64Decode(req.getParam("url")))
    //val params = JsonObject(String(base64Decoder.decode(req.getParam("params"))))
    val body = req.getParam("body")?.let {
      Buffer.buffer(base64Decode(it))
    }
    val headers = req.getParam("headers")?.let { JsonObject(String(base64Decode(it))) }
    val proxyReq = when (req.getParam("method")) {
      HttpMethod.GET.name -> {
        httpClient.getAbs(url)
      }
      HttpMethod.POST.name -> {
        httpClient.postAbs(url)
      }
      HttpMethod.PUT.name -> {
        httpClient.putAbs(url)
      }
      HttpMethod.DELETE.name -> {
        httpClient.deleteAbs(url)
      }
      else -> {
        throw Exception("Not supported method: ${req.getParam("method")}")
      }
    }
    headers?.forEach {
      proxyReq.putHeader(it.key, it.value as String)
    }
    proxyReq.handler {
      val isHtml = it.getHeader("content-type").startsWith("text/html")
      it.headers().forEach {
        if (it.key.toLowerCase() != "content-length")
          req.response().putHeader(it.key, it.value)
        else {
          if (isHtml) {
            req.response().putHeader("Content-Length", (it.value.toInt() + payload.length).toString())
          } else {
            req.response().putHeader(it.key, it.value)
          }
        }
      }
      it.handler {
        if (isHtml) {
          val htmlToken = it.toString(Charset.defaultCharset()).replace("<head>", "<head>$payload")
          req.response().write(htmlToken)
        } else {
          req.response().write(it)
        }
      }.endHandler {
        req.response().end()
      }
    }
    if (body != null) {
      proxyReq.end(body)
    } else {
      proxyReq.end()
    }
  }
}
