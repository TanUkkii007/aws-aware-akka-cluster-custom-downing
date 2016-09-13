import Dependencies._


val commonSettings = Seq(
  organization := "github.com/TanUkkii007",
  scalaVersion := "2.11.8",
  scalacOptions ++= Seq("-feature", "-deprecation", "-unchecked", "-encoding", "UTF-8", "-language:implicitConversions", "-language:postfixOps"),
  licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
  resolvers += Resolver.bintrayRepo("tanukkii007", "maven")
)

val noPublishSettings = Seq(
  publish := (),
  publishArtifact in Compile := false
)

lazy val root = (project in file("."))
  .settings(noPublishSettings)
  .aggregate(`elb-aware-akka-cluster-custom-downing`)

lazy val `elb-aware-akka-cluster-custom-downing` = (project in file("elb-aware-akka-cluster-custom-downing"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      Akka.actor,
      Akka.cluster,
      AkkaClusterCustomDowning.customDowning,
      AkkaClusterAwsEC2MetadataReplication.ec2MetadataReplication,
      AkkaHttpAws.core,
      AkkaHttpAws.elasticLoadBalancing,
      Akka.testKit,
      ScalaTest.scalaTest
    )
  )
