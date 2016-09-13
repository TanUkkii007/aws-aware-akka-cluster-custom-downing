package tanukki.akka.cluster.autodown.aws.elasticloadbalancing

import akka.ConfigurationException
import akka.actor.{Props, ActorSystem, Address}
import akka.cluster.{Cluster, DowningProvider}
import tanukki.akka.cluster.autodown.{ClusterCustomDowning, LeaderAutoDownRolesBase}
import scala.concurrent.duration.FiniteDuration
import scala.collection.JavaConverters._

final class LeaderAutoDowningRolesIfUnhealthy(system: ActorSystem) extends DowningProvider {

  private def clusterSettings = Cluster(system).settings

  override def downRemovalMargin: FiniteDuration = clusterSettings.DownRemovalMargin

  override def downingActorProps: Option[Props] = {
    val roles = system.settings.config.getStringList("custom-downing.aws.elasticloadbalancing.leader-auto-downing-roles-if-unhealthy.target-roles").asScala.toSet
    clusterSettings.AutoDownUnreachableAfter match {
      case d: FiniteDuration => if (roles.isEmpty) None else Some(LeaderAutoDownRolesIfUnhealthy.props(roles, d, ElasticLoadBalancingAwareDowningSettings(system)))
      case _ =>
        throw new ConfigurationException("LeaderAutoDowningRolesIfUnhealthy downing provider selected but 'akka.cluster.auto-down-unreachable-after' not set")
    }
  }
}

private[elasticloadbalancing] class LeaderAutoDownRolesIfUnhealthy(targetRoles: Set[String], autoDownUnreachableAfter: FiniteDuration, settings: ElasticLoadBalancingAwareDowningSettings)
  extends LeaderAutoDownRolesBase(targetRoles, autoDownUnreachableAfter)
  with ClusterCustomDowning {

  val elasticLoadBalancingAwareDowning = context.actorOf(ElasticLoadBalancingAwareDowning.props(settings), "elasticLoadBalancingAwareDowning")

  override def down(node: Address): Unit = {
    elasticLoadBalancingAwareDowning ! ElasticLoadBalancingAwareDowning.DownIfRemovedFromELB(node)
  }
}

private[elasticloadbalancing] object LeaderAutoDownRolesIfUnhealthy {
  def props(targetRoles: Set[String], autoDownUnreachableAfter: FiniteDuration, settings: ElasticLoadBalancingAwareDowningSettings): Props
    = Props(new LeaderAutoDownRolesIfUnhealthy(targetRoles, autoDownUnreachableAfter, settings))
}