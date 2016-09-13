package tanukki.akka.cluster.autodown.aws.elasticloadbalancing

import akka.actor._
import akka.cluster.{Member, Cluster}
import akka.cluster.ClusterEvent._

private[elasticloadbalancing] class ElasticLoadBalancingAwareDowning(settings: ElasticLoadBalancingAwareDowningSettings) extends Actor with ActorLogging {
  import ElasticLoadBalancingAwareDowning._

  val cluster = Cluster(context.system)

  var unreachableMember: Set[Member] = Set()

  def receive: Receive = {
    case CurrentClusterState(_, unreachable, _, _, _) =>
      unreachableMember = unreachable
    case UnreachableMember(member) =>
      unreachableMember += member
    case ReachableMember(member) =>
      unreachableMember -= member
    case MemberRemoved(member, _) =>
      unreachableMember -= member
    case UnderlyingElasticLoadBalancingAwareDowning.CheckIfNodeIsReachableAgain(address) =>
      unreachableMember.find(_.address == address) match {
        case Some(_) => self ! DownIfRemovedFromELB(address)
        case None => log.info("Member [{}] will not be downed because it become reachable again.", address)
      }
    case UnderlyingElasticLoadBalancingAwareDowning.DownCompleted(address) =>
    case msg: DownIfRemovedFromELB =>
      createDowningActor forward UnderlyingElasticLoadBalancingAwareDowning.DownIfRemovedFromELB(msg.address)
  }

  def createDowningActor: ActorRef = context.actorOf(UnderlyingElasticLoadBalancingAwareDowning.props(self, settings))

  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsSnapshot, classOf[ReachabilityEvent], classOf[MemberRemoved])
    super.preStart()
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    super.postStop()
  }
}

private[elasticloadbalancing] object ElasticLoadBalancingAwareDowning {
  case class DownIfRemovedFromELB(address: Address)

  def props(settings: ElasticLoadBalancingAwareDowningSettings) = Props(new ElasticLoadBalancingAwareDowning(settings))
}