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
import java.lang.IllegalArgumentException
import java.nio.charset.Charset
import java.util.*

class WebTest : AbstractVerticle() {
  data class ProxyRequest(val method:String,val protocol:String,val domain:String,val uri:String)
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

  private val httpClient by lazy { vertx.createHttpClient(HttpClientOptions().setKeepAlive(false)) }
  private val basicHeader by lazy {
    config().getJsonObject("headers")
  }
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
        when {
          it.path().startsWith("/proxy") -> {
            handleRequest(it)
          }
          it.path().startsWith("/index") -> {
            it.response().end(index)
          }
          else -> {
            throw FileNotFoundException("Path not found");
          }
        }
      } catch (e: FileNotFoundException) {
        logger.error("Path not found: ${it.path()}")
        //it.response().setStatusCode(500).end(e.message)
      } catch (e: Throwable) {
        it.response().setStatusCode(500).end(e.message)
        e.printStackTrace()
      }
    }.listen(httpServerPort) {
      logger.info("Listen at $httpServerPort")
    }
  }

  private fun handleRequest(req: HttpServerRequest) {
    val parsed = parseURI(req.uri()) ?: throw IllegalArgumentException()
    val url = "${parsed.protocol}://${parsed.domain}" + if(parsed.uri.startsWith("/")) { parsed.uri }else { "/${parsed.uri}" }
    val headers = JsonObject()
    headers.mergeIn(basicHeader)
    req.getParam("headers")?.let { headers.mergeIn(JsonObject(String(base64Decode(it)))) }
    val referer = req.getHeader("Referer")?.let{ parseURI(it) }
    referer?.let {
      headers.put("Referer","${it.protocol}://${it.domain}" + if(it.uri.startsWith("/")) { it.uri }else { "/${it.uri}" })
    }
    logger.info("[Req]: $url")
    req.bodyHandler { body ->
      val proxyReq = when (parsed.method) {
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
      headers.forEach {
        proxyReq.putHeader(it.key, it.value as String)
      }
      proxyReq.handler {
        val isText = it.getHeader("Content-Type").startsWith("text/")
        it.headers().filter {
          it.key != "Content-Length" && it.key!="Set-Cookie"
        }.forEach {
          req.response().putHeader(it.key, it.value)
        }
        if (isText) {
          val isHtml = it.getHeader("Content-Type").startsWith("text/html")
          it.bodyHandler {
            if (isHtml) {
              val htmlToken = it.toString(Charset.defaultCharset()).replace("<head>", "<head>$payload")
              req.response().end(Buffer.buffer(htmlToken))
            } else {
              req.response().end(it)
            }
          }
        } else {
          req.response().putHeader("Content-Length", it.getHeader("Content-Length"))
          it.handler {
            req.response().write(it)
          }.endHandler {
            req.response().end()
          }
        }
      }
      if (body != null && body.length()>0) {
        proxyReq.end(body)
      } else {
        proxyReq.end()
      }
    }
  }

  private fun parseURI(uri:String):ProxyRequest?{
    if(!uri.contains("/proxy")){
      return null
    }
    val params = uri.substring("/proxy".length).split("/")
    val method = params[1]
    val protocol = params[2]
    val domain = params[3]
    return ProxyRequest(params[1],params[2],params[3],uri.substring("/proxy".length + 1 + method.length + 1 + protocol.length + 1 + domain.length))
  }
}
