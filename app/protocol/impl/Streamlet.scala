package protocol.impl

import java.math.BigInteger
import java.security.MessageDigest

import mpi.AuthenticatedMPI.SignedPayload
import mpi.AuthenticatedNetwork
import protocol.{NodeBehavior, Protocol}

case class Transaction(src: Int, dst: Int, amt: Double)
case class Block(hash: String, epoch: Int, txs: Seq[Transaction])
case class Blockchain(blocks: Seq[Block])

case class StreamletState(
  chains: Seq[Blockchain],
  blockVotes: Map[Block, Set[Int]],
  notarizedBlocks: Set[Block],
  finalizedTransactions: Seq[Transaction],
  proposed: Boolean
)

object Streamlet extends Protocol[StreamletState, Seq[Transaction]] with AuthenticatedNetwork {
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
        broadcast(newBlock)
        send(newBlock, ctx.nodeId)
        goto(state.copy(proposed = true))
      }
    }

    override def receive: Receive = {
      case (SignedPayload(block: Block, chain, wrapper), _) =>
//        println(s"[R$round] Node ${ctx.nodeId} received $block with chain $chain")
//        println(s"[R$round] State: $state")
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
          } else {
//            println(s"[R$round ${ctx.nodeId}] Cannot fit block $block onto a notarized chain!")
          }
        }

        val newVotes = state.blockVotes.updatedWith(block) {
          case Some(existing) => Some(existing | chain.toSet)
          case None => Some(chain.toSet)
        }

        goto(state.copy(blockVotes = newVotes))

        if (newVotes.get(block).map(_.size).getOrElse(0) >= math.ceil((2.0 * config.numNodes) / 3)) {
//          println(s"${ctx.nodeId} notarizing block $block from votes ${newVotes.get(block)}")
          goto(state.copy(notarizedBlocks = state.notarizedBlocks + block))
        }

        longestNotarizedChains foreach { bc =>
          val blocks = bc.blocks
          2 until blocks.length foreach { i =>
            if (blocks(i).epoch == blocks(i - 1).epoch + 1 && blocks(i - 1).epoch == blocks(i - 2).epoch + 1) {
              val txs = 0 until i flatMap { blocks(_).txs } dropWhile state.finalizedTransactions.contains
//              println(s"Finalizing transaction @ [0:$i]: $txs")
              goto(state.copy(finalizedTransactions = state.finalizedTransactions ++ txs))
            }
          }
        }
    }

    override def afterRound(): Unit = {
      if (round == config.numRounds) {
        println(s"Finalized transactions for ${ctx.nodeId}: ${state.finalizedTransactions}")
        output(state.finalizedTransactions)
        terminate()
      }
    }
  }
}
