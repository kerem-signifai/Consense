import java.math.BigInteger
import java.security.MessageDigest

import codec.Codec
import mpi.AuthenticatedMPI.SignedPayload
import mpi.AuthenticatedNetwork
import org.scalatest.matchers.must
import org.scalatest.wordspec.AnyWordSpec
import protocol.impl.{Block, Streamlet, Transaction, _}
import protocol.{NodeBehavior, Protocol}
import simulation.{RandomHonestInitializer, _}

object EarlyFinalizedStreamlet extends Protocol[StreamletState, Seq[Transaction]] with AuthenticatedNetwork {
  private final val genesisBlock = Block("genesis", 0, Seq.empty)
  private def sha256(str: String) = String.format("%032x", new BigInteger(1, MessageDigest.getInstance("SHA-256").digest(str.getBytes("UTF-8"))))

  override def initialState: StreamletState = StreamletState(Seq(Blockchain(Seq(genesisBlock))), Map.empty, Set(genesisBlock), Seq.empty, proposed = false)
  override def consistent(o1: Seq[Transaction], o2: Seq[Transaction]): Boolean = o1.startsWith(o2) || o2.startsWith(o1)
  override def senderValidityProp(initValue: Any, o1: Seq[Transaction]): Boolean = true

  override def behavior: BehaviorGen = ctx => new NodeBehavior(ctx) {
    private val inputTxs = config.initValue.asInstanceOf[Map[Int, Seq[Transaction]]]

    private def currentEpoch = (round + 1) / 2
    private def isEpochStart = (round + 1) % 2 == 0
    private def currentLeader = (currentEpoch.hashCode() % config.numNodes) + 1

    private def longestNotarizedChains: Seq[Blockchain] = {
      val notarized = state.chains map { bc =>
        Blockchain(bc.blocks.takeWhile(state.notarizedBlocks.contains))
      } filterNot (_.blocks.isEmpty) sortBy (_.blocks.length)
      notarized.reverse match {
        case Nil => Nil
        case h :: t => h :: t.takeWhile(_.blocks.length == h.blocks.length)
      }
    }

    override def beforeRound(): Unit = {
      if (isEpochStart && ctx.nodeId == currentLeader) {
        println(s"Node ${ctx.nodeId} is the leader for epoch $currentEpoch (round $round)")
        val txs = if (state.proposed) Seq.empty else inputTxs.getOrElse(ctx.nodeId, Seq.empty)
        println(s"Our transactions: $txs")
        val newBlock = Block(sha256(longestNotarizedChains.maxBy(_.blocks.last.epoch).blocks.last.toString), currentEpoch, txs)
        if (txs.nonEmpty) {
          broadcast(newBlock)
          send(newBlock, ctx.nodeId)
        }
        goto(state.copy(proposed = true))
      }
    }

    override def receive: Receive = {
      case (SignedPayload(block: Block, chain, wrapper), _) =>
        println(s"[R$round] Node ${ctx.nodeId} received $block with chain $chain")
        println(s"[R$round] State: $state")

        // find all longest notarized chains that this block can be appended to
        // append this block to all those chains

        if (!state.blockVotes.contains(block)) {
          // Find all longest chains that this new block can extend from and add the new chains to our state
          val newChains = longestNotarizedChains.filter(bc => sha256(bc.blocks.last.toString) == block.hash) map { bc =>
            Blockchain(bc.blocks :+ block)
          }
          goto(state.copy(chains = state.chains ++ newChains))
          // If that block extends from one of the longest chains, vote for it
          if (newChains.nonEmpty) {
            broadcast(wrapper)
            goto(state.copy(blockVotes = state.blockVotes.updated(block, Set(ctx.nodeId))))
          }
        }

        val newVotes = state.blockVotes.updatedWith(block) {
          case Some(existing) => Some(existing | chain.toSet)
          case None => Some(chain.toSet)
        }

        goto(state.copy(blockVotes = newVotes))

        if (newVotes.get(block).map(_.size).getOrElse(0) >= math.ceil((2.0 * config.numNodes) / 3)) {
          println(s"${ctx.nodeId} notarizing block $block from votes ${newVotes.get(block)}")
          goto(state.copy(notarizedBlocks = state.notarizedBlocks + block))
        }

        longestNotarizedChains foreach { bc =>
          println(s"[R$round ${ctx.nodeId}] Checking chain $bc")
          val blocks = bc.blocks
          1 until blocks.length foreach { i =>
            val txs = 0 to i flatMap { blocks(_).txs } dropWhile state.finalizedTransactions.contains
            println(s"Finalizing transaction @ [0:$i]: $txs")

            goto(state.copy(finalizedTransactions = state.finalizedTransactions ++ txs))
          }
        }
    }

    override def afterRound(): Unit = {
      if (round == config.numRounds) {
        output(state.finalizedTransactions)
        println(state.finalizedTransactions)
        terminate()
      }
    }
  }
}


class StreamletSpec extends AnyWordSpec with must.Matchers {

  "A Streamlet implementation" should {
    "have consistent blockchains in the non-failure case" in {
      val n = 4
      val f = 0
      val init = Map(
        1 -> Seq(Transaction(1, 2, 1)),
        2 -> Seq(Transaction(2, 1, 5)),
        3 -> Seq(Transaction(3, 4, 2)),
        4 -> Seq(Transaction(4, 1, 1))
      )
      val config = Config.builder
        .withInitValue(true)
        .withMaxCorruptNodes(f)
        .withHonestNodes(n - f)
        .withRounds(n * 2 + 6) // Run one epoch per-node, then allow 3 ``buffer'' epochs to finalize all transactions
        .withInitPolicy(RandomHonestInitializer(1))
        .withInitValue(init)
      val result = new Simulation(config.build, Streamlet).start()
      result.honestAgree mustBe true
      result.honestSenderProposition mustBe true
    }

    "have consistent blockchains without ``buffer'' epochs" in {
      val n = 4
      val f = 0
      val init = Map(
        1 -> Seq(Transaction(1, 2, 1)),
        2 -> Seq(Transaction(2, 1, 5)),
        3 -> Seq(Transaction(3, 4, 2)),
        4 -> Seq(Transaction(4, 1, 1))
      )
      val config = Config.builder
        .withInitValue(true)
        .withMaxCorruptNodes(f)
        .withHonestNodes(n - f)
        .withRounds(n * 2) // Run one epoch per-node
        .withInitPolicy(RandomHonestInitializer(1))
        .withInitValue(init)
      val result = new Simulation(config.build, Streamlet).start()
      result.honestAgree mustBe true
      result.honestSenderProposition mustBe true
    }

    "have consistent blockchains in the presence of less than n/3 crash failures" in {
      val n = 4
      val f = 1
      val init = Map(
        1 -> Seq(Transaction(1, 2, 1)),
        2 -> Seq(Transaction(2, 1, 5)),
        3 -> Seq(Transaction(3, 4, 2))
      )
      val config = Config.builder
        .withInitValue(true)
        .withMaxCorruptNodes(f)
        .withHonestNodes(n - f)
        .withRounds(n * 2 + 6) // Run one epoch per-node
        .withInitPolicy(RandomHonestInitializer(1))
        .withInitValue(init)
      1 to f foreach { _ =>
        config.addCorruptNode { (_, ctx) =>
          new NodeBehavior(ctx) {
            override def beforeRound(): Unit = {
            }
          }
        }
      }
      val result = new Simulation(config.build, Streamlet).start()
      result.honestAgree mustBe true
      result.honestSenderProposition mustBe true
      //      println(Codec.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result.trace))

    }

    "have consistent blockchains in the presence of less than n/3 malicious failures" in {
      val n = 4
      val f = 1
      val init = Map(
        1 -> Seq(Transaction(1, 2, 1)),
        2 -> Seq(Transaction(2, 1, 5)),
        3 -> Seq(Transaction(3, 4, 2))
      )
      val config = Config.builder
        .withInitValue(true)
        .withMaxCorruptNodes(f)
        .withHonestNodes(n - f)
        .withRounds(n * 2 + 6) // Run one epoch per-node
        .withInitPolicy(RandomHonestInitializer(1))
        .withInitValue(init)
      1 to f foreach { _ =>
        config.addCorruptNode { (_, ctx) => new NodeBehavior(ctx) {} }
      }
      val result = new Simulation(config.build, Streamlet).start()
      result.honestAgree mustBe true
      result.honestSenderProposition mustBe true
    }
  }

  "An incorrect Streamlet implementation" should {
    "have inconsistent blockchain with an invalid finalization protocol" in {
      val n = 5
      val f = 1
      val init = Map(
        1 -> Seq(),
        2 -> Seq(),
        3 -> Seq(),
        4 -> Seq(),
      )
      val config = Config.builder
        .withInitValue(true)
        .withMaxCorruptNodes(f)
        .withHonestNodes(n - f)
        .withRounds(n * 2 + 6) // Run one epoch per-node
        .withInitPolicy(RandomHonestInitializer(1))
        .withInitValue(init)
      1 to f foreach { _ =>
        config.addCorruptNode { (adv, ctx) =>
          new NodeBehavior(ctx) {
            private def currentEpoch = (round + 1) / 2
            private def isEpochStart = (round + 1) % 2 == 0
            private def currentLeader = (currentEpoch.hashCode() % config.numNodes) + 1
            private final val genesisBlock = Block("genesis", 0, Seq.empty)
            private def sha256(str: String) = String.format("%032x", new BigInteger(1, MessageDigest.getInstance("SHA-256").digest(str.getBytes("UTF-8"))))
            private val tx1 = Seq(Transaction(1, 1, 1))
            private val tx2 = Seq(Transaction(2, 2, 2))
            override def beforeRound(): Unit = {
              if (isEpochStart && currentLeader == ctx.nodeId) {
                println(s"Adversary is leader for epoch ${currentEpoch} @ round $round")

                val b1 = Block(sha256(genesisBlock.toString), currentEpoch, tx1)
                val b2 = Block(sha256(genesisBlock.toString), currentEpoch, tx2)
                send(b1, 1)
                send(b1, 2)
                send(b2, 3)
                send(b2, 4)
              }
            }
          }
        }
      }
      val result = new Simulation(config.build, EarlyFinalizedStreamlet).start()
      println(Codec.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result.trace))

      result.honestAgree mustBe false
      result.honestSenderProposition mustBe false
    }
  }
}