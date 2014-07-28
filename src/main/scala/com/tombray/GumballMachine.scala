package com.tombray

import akka.actor.{FSM, Actor}

/**
 * Created by tbray on 6/23/14.
 */
object GumballMachineProtocol {
  case object InsertQuarter
  case object EjectQuarter
  case object TurnCrank
  case object GumballCount
}

object GumballMachine {
  sealed trait State
  case object NoQuarterState extends State
  case object HasQuarterState extends State
  case object SoldState extends State
  case object SoldOutState extends State
}

class GumballMachine extends Actor with FSM[GumballMachine.State, Int] {
  import GumballMachine._
  import com.tombray.GumballMachineProtocol._

  startWith(NoQuarterState, 5)

  when(NoQuarterState) {
    case Event(InsertQuarter, gumballCount) if gumballCount > 0 => goto(HasQuarterState)
  }

  when(HasQuarterState) {
    case Event(EjectQuarter,_) => goto(NoQuarterState)
    case Event(TurnCrank, _) => goto(SoldState)
  }

  when(SoldState) {
    case _ => stay
  }

  whenUnhandled {
    case Event(GumballCount, gumballCount) => sender() ! gumballCount; stay()
    case x => println(x); stay()
  }

  initialize()
}
