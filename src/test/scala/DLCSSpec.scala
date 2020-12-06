import codec.Codec
import org.scalatest.matchers.must
import org.scalatest.wordspec.AnyWordSpec
import protocol.NodeBehavior
import protocol.impl._
import simulation.{RandomHonestInitializer, _}

class DLCSSpec extends AnyWordSpec with must.Matchers {

  "A weakly-valid DLCS implementation" should {
    "reach consensus in the presence of no failures" in {
      val n = 15
      val f = 0
      val config = Config.builder
        .withMaxCorruptNodes(f)
        .withHonestNodes(n - f)
        .withRounds(n)
        .withInitPolicy(DeterministicInitializer(Seq(1)))
        .withInitValue(DLCSConfig(initValue = true, (1 to n map (i => i -> i)).toMap))
      val result = new Simulation(config.build, DLCSConsensus).start()
      result.honestAgree mustBe true
      result.honestSenderProposition mustBe true
    }

    "reach weakly-valid consensus in the presence of malicious leaders" in {
      val n = 4
      val f = 1
      val config = Config.builder
        .withMaxCorruptNodes(f)
        .withHonestNodes(n - f)
        .withRounds(n)
        .withInitPolicy(DeterministicInitializer(Seq(1)))
        .withInitValue(DLCSConfig(initValue = false, Map(
          1 -> 4,
          2 -> 1,
          3 -> 2,
          4 -> 3
        )))
      1 to f foreach { _ =>
        config.addCorruptNode { (_, ctx) =>
          new NodeBehavior(ctx) {
            override def afterRound(): Unit = {
              if (round == 1) {
                broadcast(DLCSMessage(value = true, round))
              }
            }
          }
        }
      }
      val result = new Simulation(config.build, DLCSConsensus).start()
      println(Codec.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result.trace))

      result.honestAgree mustBe true
      result.honestSenderProposition mustBe false
    }
  }

  "An strongly-consistent DLCS implementation" should {
    "reach failure in the presence of malicious nodes" in {
      val n = 4
      val f = 1
      val config = Config.builder
        .withMaxCorruptNodes(f)
        .withHonestNodes(n - f)
        .withRounds(f + 1)
        .withInitPolicy(RandomHonestInitializer(1))
        .withInitValue(true)
      1 to f foreach { _ =>
        config.addCorruptNode { (_, ctx) =>
          new NodeBehavior(ctx) {
            override def afterRound(): Unit = {
                broadcast(false)
            }
          }
        }
      }
      val result = new Simulation(config.build, StrongDLCSConsensus).start()
      println(Codec.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result.trace))

      result.honestAgree mustBe true
      result.honestSenderProposition mustBe true
    }

    "be susceptible to same attack as Dolev-Strong" in {
      val n = 4
      val f = 1
      val config = Config.builder
        .withMaxCorruptNodes(f)
        .withHonestNodes(n - f)
        .withRounds(f)
        .withInitPolicy(RandomHonestInitializer(1))
        .withInitValue(false)
      1 to f foreach { _ =>
        config.addCorruptNode { (_, ctx) =>
          new NodeBehavior(ctx) {
            override def beforeRound(): Unit = {
              broadcast(true)
            }
          }
        }
      }
      val result = new Simulation(config.build, StrongDLCSConsensus).start()
      result.honestAgree mustBe true
      result.honestSenderProposition mustBe false
    }
  }
}