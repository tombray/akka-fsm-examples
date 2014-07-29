package com.tombray.examples.akka

import akka.actor.{ActorLogging, FSM, Actor}
import com.tombray.examples.akka.ElevatorActor.{Data, State}
import com.tombray.examples.akka.ElevatorProtocol.Request
import scala.concurrent.duration._
/**
 * Created by tbray on 7/24/14.
 */
class ElevatorActor extends Actor with ActorLogging with FSM[State,Data]{
  import ElevatorActor._
  import ElevatorProtocol._

  startWith(Idle, Data(NoDirection, 0, Set.empty[Request]))

  when(Idle) {
    case Event(req:Request, Data(_,currentFloor, _)) if req.floor == currentFloor => goto(Open)
    case Event(req:Request, Data(_,currentFloor, requests)) if req.floor < currentFloor => goto(GoingDown) using Data(Down, currentFloor, requests + req)
    case Event(req:Request, Data(_,currentFloor, requests)) if req.floor > currentFloor => goto(GoingUp) using Data(Up, currentFloor, requests + req)
  }

  when(GoingUp) (going(Up))
  when(GoingDown) (going(Down))

  //whether we're GoingUp or GoingDown, the logic to handle incoming events is very similar
  def going(sameDirection:Direction):StateFunction = {
    val oppositeDirection = if (sameDirection == Up) Down else Up
    def moreRequestsInSameDirectionFn(floor:Int, direction:Direction, requests:Set[Request]) = if (direction == Up) hasRequestsForHigherFloors(floor, requests) else hasRequestsForLowerFloors(floor, requests)
    def needToSwitchDirections( direction:Direction, floor:Int, requests:Set[Request]) = !moreRequestsInSameDirectionFn(floor,direction,requests) && requests.contains(GetOn(floor,oppositeDirection))
    def removeAllRequestsForFloor(floor:Int, requests:Set[Request]) = requests.filter(_.floor != floor)
    def hasRequestForFloor(floor:Int, requests:Set[Request], direction:Direction): Boolean = requests.contains(GetOff(floor)) || requests.contains(GetOn(floor, direction))

    return {
      case Event(request:Request, d) => stay using Data(sameDirection, d.currentFloor, d.requests + request)
      case Event(ArrivedAtFloor(floor), d) if hasRequestForFloor(floor,d.requests,sameDirection) => goto(Open) using Data(sameDirection, floor, removeAllRequestsForFloor(floor, d.requests))
      case Event(ArrivedAtFloor(floor), d) if needToSwitchDirections(d.direction, floor, d.requests) => goto(Open) using Data(oppositeDirection, floor, removeAllRequestsForFloor(floor, d.requests))
    }
  }

  when(Open, 3 seconds) {
    case Event(request:Request, Data(NoDirection,currentFloor,requests)) if request.floor < currentFloor => stay using Data(Down, currentFloor, requests + request)
    case Event(request:Request, Data(NoDirection,currentFloor,requests)) if request.floor > currentFloor => stay using Data(Up, currentFloor, requests + request)
    case Event(request:Request, d@Data(_,currentFloor,requests)) => stay using d.copy(requests = requests + request) //don't change direction
    case Event(StateTimeout,d) => {

      if (d.direction == Down && hasRequestsForLowerFloors(d.currentFloor, d.requests)) goto(GoingDown)
      else if (d.direction == Up && hasRequestsForHigherFloors(d.currentFloor, d.requests)) goto(GoingUp)
      else {
        if (hasRequestsForHigherFloors(d.currentFloor,d.requests)) goto(GoingUp) using d.copy(direction = Up)
        else if (hasRequestsForLowerFloors(d.currentFloor, d.requests)) goto(GoingDown) using d.copy(direction = Down)
        else stay() //TODO should goto(Idle), write a test first
      }
    }
  }

  whenUnhandled {
    case Event(ArrivedAtFloor(floor),_) => stay using stateData.copy(currentFloor = floor)
    case Event(GetCurrentFloor, Data(_,currentFloor,_)) => sender ! currentFloor; stay()
    case Event(GetRequests,d) => sender ! d.requests; stay()
  }

  onTransition {
    case _ -> Open => log.info("opening doors. {}", nextStateData)
    case Open -> _ => log.info("closing doors. {}", nextStateData)
  }

  def hasRequestsForLowerFloors(floor:Int, requests:Set[Request]) = { requests.exists( _.floor < floor)}
  def hasRequestsForHigherFloors(floor:Int, requests:Set[Request]) = { requests.exists( _.floor > floor)}
}

object ElevatorActor {
  sealed trait Direction
  case object Up extends Direction
  case object Down extends Direction
  case object NoDirection extends Direction

  sealed trait State
  case object Idle extends State
  case object Open extends State
  case object GoingDown extends State
  case object GoingUp extends State

  case class Data(direction:Direction, currentFloor:Int, requests:Set[Request])
}

object ElevatorProtocol {
  import ElevatorActor._

  sealed trait Request { val floor:Int }
  case class GetOn(floor:Int, direction:Direction) extends Request
  case class GetOff(floor:Int) extends Request

  case object GetRequests
  case object GetCurrentFloor
  case class ArrivedAtFloor(floor:Int)
}
