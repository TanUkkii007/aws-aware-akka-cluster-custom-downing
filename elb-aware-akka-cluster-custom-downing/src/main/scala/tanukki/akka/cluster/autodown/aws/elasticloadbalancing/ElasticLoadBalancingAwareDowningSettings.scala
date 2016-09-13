package tanukki.akka.cluster.autodown.aws.elasticloadbalancing

import akka.actor.ActorSystem
import com.amazonaws.regions.{Regions, Region}

case class ElasticLoadBalancingAwareDowningSettings(region: Region, loadBalancerName: String) {
  require(loadBalancerName.nonEmpty, "loadBalancerName should not be empty")
}

object ElasticLoadBalancingAwareDowningSettings {
  def apply(system: ActorSystem): ElasticLoadBalancingAwareDowningSettings = {
    val c = system.settings.config
    ElasticLoadBalancingAwareDowningSettings(
      Region.getRegion(Regions.fromName(c.getString("custom-downing.aws.elasticloadbalancing.leader-auto-downing-roles-if-unhealthy.region"))),
      c.getString("custom-downing.aws.elasticloadbalancing.leader-auto-downing-roles-if-unhealthy.loadbalancer-name")
    )
  }
}