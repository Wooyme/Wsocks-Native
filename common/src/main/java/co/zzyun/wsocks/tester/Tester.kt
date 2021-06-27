package co.zzyun.wsocks.tester

import io.vertx.core.Future

interface Tester {
  fun test(ips:List<String>): Future<String>
}
