import codec.Codec
import mpi.AuthenticatedMPI.SignedPayload
import org.scalatest.matchers.must
import org.scalatest.wordspec.AnyWordSpec
import protocol._
import protocol.impl.DolevStrong
import simulation.{RandomCorruptInitializer, RandomHonestInitializer, _}

class DolevStrongSpec extends AnyWordSpec with must.Matchers {

  "A Dolev-Strong implementation" should {
    "reach consensus with all honest nodes" in {
      val n = 4
      val f = 1
      val config = Config.builder
        .withInitValue(true)
        .withMaxCorruptNodes(f)
        .withHonestNodes(n - f)
        .withRounds(f + 1)
        .withInitPolicy(RandomHonestInitializer(1))
        .withInitValue(true)
      val result = new Simulation(config.build, DolevStrong).start()
      SpecRecorder.record(result, "Dolev-Strong", "Reaching consensus in the presence of no failures")
      result.honestAgree mustBe true
      result.honestSenderProposition mustBe true

    }

    "reach consensus in the presence of arbitrary number of failures (no-op malicious actors)" in {
      val n = 4
      val f = 1
      val config = Config.builder
        .withInitValue(true)
        .withMaxCorruptNodes(f)
        .withHonestNodes(n - f)
        .withRounds(f + 1)
        .withInitPolicy(RandomHonestInitializer(1))
        .withInitValue(true)
      1 to f foreach { _ =>
        config.addCorruptNode { (_, ctx) => new NodeBehavior(ctx) {} }
      }
      val result = new Simulation(config.build, DolevStrong).start()
      SpecRecorder.record(result, "Dolev-Strong", "Reaching consensus in the presence of crash failures")
      result.honestAgree mustBe true
      result.honestSenderProposition mustBe true
    }

    "fail to reach consensus when protocol is run for less than `f + 1` rounds (§3.4.1 FDCB)" in {
      // In round 0, the corrupt sender broadcasts TRUE
      // From round 1..f-1, the corrupt nodes propagate FALSE and create a message FALSE with a signature chain of length f
      // In round f-1, the corrupt nodes send FALSE with a chain of f malicious signatures to an honest node
      // In round f, the aforementioned honest node accepts the malicious message and arrives at an inconsistent state
      val n = 7
      val f = 2
      val config = Config.builder
        .withInitValue(null)
        .withMaxCorruptNodes(f)
        .withHonestNodes(n - f)
        .withRounds(f)
        .withInitPolicy(RandomCorruptInitializer(1))
      1 to f foreach { _ =>
        config.addCorruptNode { (advCtx, nCtx) =>
          new NodeBehavior(nCtx) {
            override def initialize(): Unit = {
              advCtx.discovery.honestNodes foreach { send(true, _) }
              advCtx.discovery.corruptNodes foreach { send(false, _) }
            }
            override def receive: Receive = {
              case (SignedPayload(value: Boolean, _, wrapper), _) =>
                if (!value) {
                  if (round == f - 1) {
                    send(wrapper, advCtx.discovery.honestNodes.head)
                  } else {
                    advCtx.discovery.corruptNodes foreach { send(wrapper, _) }
                  }
                }
            }
          }
        }
      }
      val result = new Simulation(config.build, DolevStrong).start()
      result.honestSenderProposition mustBe true
      result.honestAgree mustBe false
      println(Codec.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result.trace))

      SpecRecorder.record(result, "Dolev-Strong", "Fail to reach consensus in the presence of malicious leaders when run for less than f + 1 rounds")
    }

    "reach consensus with malicious actor in `f + 1` rounds (§3.4.1 FDCB)" in {
      // In round 0, the corrupt sender broadcasts TRUE
      // From round 1..f-1, the corrupt nodes propagate FALSE and create a message FALSE with a signature chain of length f
      // In round f-1, the corrupt nodes send FALSE with a chain of f malicious signatures to an honest node
      // In round f, the aforementioned honest node accepts the malicious message and arrives at an inconsistent state
      // Since we run for f + 1 rounds, a malicious chain of signatures must include at least one honest node which would have properly propagated that value to other honest nodes
      val n = 10
      val f = 3
      val config = Config.builder
        .withInitValue(null)
        .withMaxCorruptNodes(f)
        .withHonestNodes(n - f)
        .withRounds(f + 1)
        .withInitPolicy(RandomCorruptInitializer(1))
      1 to f foreach { _ =>
        config.addCorruptNode { (advCtx, nCtx) =>
          new NodeBehavior(nCtx) {
            override def initialize(): Unit = {
              advCtx.discovery.honestNodes foreach { send(true, _) }
              advCtx.discovery.corruptNodes foreach { send(false, _) }
            }
            override def receive: Receive = {
              case (SignedPayload(value: Boolean, _, wrapper), _) =>
                if (!value) {
                  if (round == f - 1) {
                    send(wrapper, advCtx.discovery.honestNodes.head)
                  } else {
                    advCtx.discovery.corruptNodes foreach { send(wrapper, _) }
                  }
                }
            }
          }
        }
      }
      val result = new Simulation(config.build, DolevStrong).start()
      result.honestSenderProposition mustBe true
      result.honestAgree mustBe true
      SpecRecorder.record(result, "Dolev-Strong", "Reach consensus in the presence of malicious leaders when run for f + 1 rounds")
    }
  }
}
