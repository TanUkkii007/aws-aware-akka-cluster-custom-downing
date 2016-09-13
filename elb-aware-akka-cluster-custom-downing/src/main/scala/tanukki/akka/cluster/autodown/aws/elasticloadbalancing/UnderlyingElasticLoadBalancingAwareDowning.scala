package tanukki.akka.cluster.autodown.aws.elasticloadbalancing

import akka.actor._
import akka.cluster.Cluster
import akka.stream.ActorMaterializer
import com.amazonaws.services.elasticloadbalancing.model.{Instance, DescribeInstanceHealthRequest}
import tanukkii.akka.cluster.aws.ec2metadata.{EC2ClusterMember, EC2MetadataReplication}
import tanukkii.akka.cluster.aws.ec2metadata.EC2MetadataReplicator.{CurrentClusterEC2Metadata, GetClusterEC2Metadata}
import tanukkii.akkahttp.aws.elasticloadbalancing.{ElasticLoadBalancingConnection, ElasticLoadBalancingClient}
import akka.pattern.pipe
import scala.collection.JavaConverters._
import scala.concurrent.duration._

private class UnderlyingElasticLoadBalancingAwareDowning(sendTo: ActorRef, settings: ElasticLoadBalancingAwareDowningSettings) extends Actor with ActorLogging {
  import UnderlyingElasticLoadBalancingAwareDowning._
  import context.system
  import context.dispatcher

  implicit val materializer = ActorMaterializer()(context)

  val cluster = Cluster(context.system)

  val eC2MetadataReplicator = EC2MetadataReplication(context.system).replicator

  implicit val elbConnection = ElasticLoadBalancingConnection(settings.region)

  def receive: Receive = getEC2Metadata

  def getEC2Metadata: Receive = {
    case DownIfRemovedFromELB(address) =>
      eC2MetadataReplicator ! GetClusterEC2Metadata
      context.become(waitForEC2Metadata(address))
  }

  def waitForEC2Metadata(address: Address): Receive = {
    case CurrentClusterEC2Metadata(members) =>
      members.find(_.address == address) match {
        case Some(EC2ClusterMember(_, info)) =>
          context.become(checkingInstanceStatus(address, info.instanceId))
          self ! CheckInstanceState
        case None =>
          log.error("Cannot find EC2 metadata of member [{}]. Retrying.", address)
          context.become(getEC2Metadata)
          system.scheduler.scheduleOnce(1 second, self, DownIfRemovedFromELB(address))
      }
  }

  def checkingInstanceStatus(address: Address, instanceId: String): Receive = {
    case CheckInstanceState =>
      val request = new DescribeInstanceHealthRequest()
        .withLoadBalancerName(settings.loadBalancerName)
        .withInstances(new Instance().withInstanceId(instanceId))
      ElasticLoadBalancingClient.describeInstanceHealth(request).map { result =>
        result.getInstanceStates.asScala.find(_.getInstanceId == instanceId) match {
          case Some(state) if state.getState == "OutOfService" =>  MemberCanBeDown
          case Some(state) => DoNotDownMember
          case None =>
        }
      }.pipeTo(self)
    case Status.Failure(e) =>
      log.error(e, "Failed to describe instance health with instanceId {} from ELB: {}", instanceId, settings.loadBalancerName)
      system.scheduler.scheduleOnce(1 second, self, CheckInstanceState)
    case MemberCanBeDown =>
      log.info("Leader is auto-downing unreachable node [{}]", address)
      cluster.down(address)
      sendTo ! DownCompleted(address)
      context.stop(self)
    case DoNotDownMember =>
      log.info("Unreachable node [{}] is still recognised as healthy by ELB: {}.", address, settings.loadBalancerName)
      sendTo ! CheckIfNodeIsReachableAgain(address)
      context.stop(self)
  }
}

private[elasticloadbalancing] object UnderlyingElasticLoadBalancingAwareDowning {
  case class DownIfRemovedFromELB(address: Address)
  case object CheckInstanceState
  case object MemberCanBeDown
  case object DoNotDownMember
  case class CheckIfNodeIsReachableAgain(address: Address)
  case class DownCompleted(address: Address)

  def props(sendTo: ActorRef, settings: ElasticLoadBalancingAwareDowningSettings): Props = Props(new UnderlyingElasticLoadBalancingAwareDowning(sendTo, settings))
}