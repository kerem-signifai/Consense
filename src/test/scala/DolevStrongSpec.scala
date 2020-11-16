import AuthenticatedMPI.SignedPayload
import org.scalatest.matchers.must
import org.scalatest.wordspec.AnyWordSpec

class DolevStrongSpec extends AnyWordSpec with must.Matchers {

  "A Dolev-Strong implementation" should {
    "reach consensus with all honest nodes" in {
      val n = 10
      val f = 3
      val config = SimulatorConfig.builder
        .withInitValue(true)
        .withMaxCorruptNodes(f)
        .withHonestNodes(n - f)
        .withRounds(f + 1)
        .withInitPolicy(RandomHonestInitializer)
        .withInitValue(true)
      val result = new Simulator(config.build, DolevStrong).start()
      result.honestAgree mustBe true
      result.honestSenderProposition mustBe true
    }

    "reach consensus in the presence of arbitrary number of failures (no-op malicious actors)" in {
      val n = 10
      val f = 3
      val config = SimulatorConfig.builder
        .withInitValue(true)
        .withMaxCorruptNodes(f)
        .withHonestNodes(n - f)
        .withRounds(f + 1)
        .withInitPolicy(RandomCorruptInitializer)
        .withInitValue(true)
      1 to f foreach { _ =>
        config.addCorruptNode { ctx => new NodeBehavior(ctx) {} }
      }
      val result = new Simulator(config.build, DolevStrong).start()
      result.honestAgree mustBe true
      result.honestSenderProposition mustBe true
    }

    "fail to reach consensus when protocol is run for less than `f + 1` rounds (§3.4.1 FDCB)" in {
      // In round 0, the corrupt sender broadcasts TRUE
      // From round 1..f-1, the corrupt nodes propagate FALSE and create a message FALSE with a signature chain of length f
      // In round f-1, the corrupt nodes send FALSE with a chain of f malicious signatures to an honest node
      // In round f, the aforementioned honest node accepts the malicious message and arrives at an inconsistent state
      val n = 10
      val f = 3
      val config = SimulatorConfig.builder
        .withInitValue(null)
        .withMaxCorruptNodes(f)
        .withHonestNodes(n - f)
        .withRounds(f)
        .withInitPolicy(RandomCorruptInitializer)
      1 to f foreach { _ =>
        config.addCorruptNode { ctx =>
          new NodeBehavior(ctx) {
            override def init(): Unit = {
              discovery.honestNodes foreach { send(true, _) }
              discovery.corruptNodes foreach { send(false, _) }
            }
            override def receive: Receive = {
              case (SignedPayload(value: Boolean, _, wrapper), _) =>
                if (!value) {
                  if (round == f - 1) {
                    send(wrapper, discovery.honestNodes.head)
                  } else {
                    discovery.corruptNodes foreach { send(wrapper, _) }
                  }
                }
            }
          }
        }
      }
      val result = new Simulator(config.build, DolevStrong).start()
      result.honestSenderProposition mustBe true
      result.honestAgree mustBe false
    }

    "reach consensus with malicious actor in `f + 1` rounds (§3.4.1 FDCB)" in {
      // In round 0, the corrupt sender broadcasts TRUE
      // From round 1..f-1, the corrupt nodes propagate FALSE and create a message FALSE with a signature chain of length f
      // In round f-1, the corrupt nodes send FALSE with a chain of f malicious signatures to an honest node
      // In round f, the aforementioned honest node accepts the malicious message and arrives at an inconsistent state
      // Since we run for f + 1 rounds, a malicious chain of signatures must include at least one honest node which would have properly propagated that value to other honest nodes
      val n = 10
      val f = 3
      val config = SimulatorConfig.builder
        .withInitValue(null)
        .withMaxCorruptNodes(f)
        .withHonestNodes(n - f)
        .withRounds(f + 1)
        .withInitPolicy(RandomCorruptInitializer)
      1 to f foreach { _ =>
        config.addCorruptNode { ctx =>
          new NodeBehavior(ctx) {
            override def init(): Unit = {
              discovery.honestNodes foreach { send(true, _) }
              discovery.corruptNodes foreach { send(false, _) }
            }
            override def receive: Receive = {
              case (SignedPayload(value: Boolean, _, wrapper), _) =>
                if (!value) {
                  if (round == f - 1) {
                    send(wrapper, discovery.honestNodes.head)
                  } else {
                    discovery.corruptNodes foreach { send(wrapper, _) }
                  }
                }
            }
          }
        }
      }
      val result = new Simulator(config.build, DolevStrong).start()
      result.honestSenderProposition mustBe true
      result.honestAgree mustBe true
    }
  }
}
