/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package config

import play.api.mvc.RequestHeader
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.hooks.HttpHooks
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.bootstrap.config.LoadAuditingConfig
import uk.gov.hmrc.play.config.{AppName, RunMode, ServicesConfig}
import uk.gov.hmrc.play.http.ws._

import scala.concurrent.Await


object AuditConnector extends Auditing with AppName with RunMode {
  override lazy val auditingConfig = LoadAuditingConfig(s"$env.auditing")
}

trait Hooks extends HttpHooks with HttpAuditing {
  override val hooks = Seq(AuditingHook)
  override lazy val auditConnector: Auditing = AuditConnector
}

trait WSHttp extends HttpGet with WSGet
  with HttpPut with WSPut
  with HttpPost with WSPost
  with HttpDelete with WSDelete
  with Hooks with AppName

object WSHttp extends WSHttp

trait WSHttpProxy extends WSHttp with WSProxy with RunMode with HttpAuditing with ServicesConfig

object WSHttpProxy extends WSHttpProxy {
  override lazy val appName = getString("appName")
  override lazy val wsProxyServer = WSProxyConfiguration(s"proxy")
  override lazy val auditConnector = AuditConnector
}

object TaiHtmlPartialRetriever extends PartialRetriever {

  override val httpGet = WSHttp

  override protected def loadPartial(url: String)(implicit request: RequestHeader): HtmlPartial =
    try {
      fetchPartial(url) match {
        case s: HtmlPartial.Success => s
        case s: HtmlPartial.Failure => throw new RuntimeException("Could not load partial")
      }
    }catch {
      case e: Exception => throw new RuntimeException("Could not load partial")
    }

  private def fetchPartial(url: String): HtmlPartial = {
    implicit val hc = HeaderCarrier()
    Await.result(httpGet.GET[HtmlPartial](url).recover(HtmlPartial.connectionExceptionsAsHtmlPartialFailure), partialRetrievalTimeout)
  }

}

object FrontendAuthConnector extends AuthConnector with ServicesConfig {
  val serviceUrl = baseUrl("auth")
  lazy val http = WSHttp
}

object FrontEndDelegationConnector extends DelegationConnector with ServicesConfig {
  override protected def serviceUrl: String = baseUrl("delegation")
  override protected def http: WSHttp = WSHttp
}