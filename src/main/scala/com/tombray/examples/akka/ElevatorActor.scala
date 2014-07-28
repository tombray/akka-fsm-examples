package com.tombray.examples.akka

import akka.actor.{ActorLogging, FSM, Actor}
import com.tombray.examples.akka.ElevatorActor.{Data, State}
import scala.concurrent.duration._
/**
 * Created by tbray on 7/24/14.
 */
class ElevatorActor extends Actor with ActorLogging with FSM[State,Data]{
  import ElevatorActor._
  import ElevatorProtocol._

  var requests:Set[Request] = Set.empty[Request]

  startWith(Idle, Data(NoDirection,0))

  when(Idle) {
    case Event(r:Request,Data(_,currentFloor)) if r.floor == currentFloor => goto(Open)
    case Event(req:Request,Data(_,currentFloor)) if req.floor < currentFloor => goto(GoingDown) using downData(req) //TODO include requests in Data()
    case Event(r:Request,Data(_,currentFloor)) if r.floor > currentFloor => { addToRequests(r); goto(GoingUp) using Data(Up, currentFloor) }
  }

  when(GoingUp) (going(sameDirection = Up, oppositeDirection = Down, hasRequestsForHigherFloors))
  when(GoingDown) (going(sameDirection = Down, oppositeDirection = Up, hasRequestsForLowerFloors))

  //whether we're GoingUp or GoingDown, the logic to handle incoming events is very similar; this function allows us to stay DRY
  def going(sameDirection:Direction, oppositeDirection:Direction, moreRequestsFn:Int => Boolean):StateFunction = {
    case Event(request:Request, _) => addToRequests(request); stay
    case Event(ArrivedAtFloor(floor),d) if requests.contains(GetOff(floor)) => goto(Open) using d.copy(currentFloor = floor)
    case Event(ArrivedAtFloor(floor),d) if requests.contains(GetOn(floor,sameDirection)) => goto(Open) using d.copy(currentFloor = floor)
    case Event(ArrivedAtFloor(floor),_) if (!moreRequestsFn(floor) && requests.contains(GetOn(floor,oppositeDirection))) => goto(Open) using Data(oppositeDirection,floor)
  }

  when(Open, 3 seconds) {
    case Event(request:Request, Data(NoDirection,currentFloor)) if request.floor < currentFloor => stayAndChangeDirectionToDown(request)
    case Event(request:Request, Data(NoDirection,currentFloor)) if request.floor > currentFloor => stayAndChangeDirectionToUp(request)
    case Event(request:Request, Data(_,currentFloor)) => requests = requests + request; stay //don't change direction
    case Event(StateTimeout,d) => {

      if (d.direction == Down && hasRequestsForLowerFloors(d.currentFloor)) goto(GoingDown)
      else if (d.direction == Up && hasRequestsForHigherFloors(d.currentFloor)) goto(GoingUp)
      else {
        if (hasRequestsForHigherFloors(d.currentFloor)) goto(GoingUp) using Data(Up, d.currentFloor)
        else if (hasRequestsForLowerFloors(d.currentFloor)) goto(GoingDown) using Data(Down, d.currentFloor)
        else stay() //TODO should goto(Idle), write a test first
      }
    }
  }

  whenUnhandled {
    case Event(ArrivedAtFloor(floor),_) => stay using stateData.copy(currentFloor = floor)
    case Event(GetCurrentFloor, Data(_,currentFloor)) => sender ! currentFloor; stay()
    case Event(GetRequests,_) => sender ! requests; stay()
  }

  onTransition {
    case _ -> Open => removeAllRequestsForFloor(nextStateData.currentFloor); log.info("opening doors. {}", nextStateData)
    case Open -> _ => log.info("closing doors. {}", nextStateData)
  }

  onTransition {
    case a -> b => log.info("transitioning from {} to {}, requests: {}", a, b, requests)
  }

  def stayAndChangeDirectionToDown(request: Request) = {
    requests = requests + request; stay using stateData.copy(direction = Down)
  }

  def stayAndChangeDirectionToUp(request: Request) = {
    requests = requests + request; stay using stateData.copy(direction = Up)
  }

  def downData(request:Request) = {
    addToRequests(request)
    stateData.copy(direction = Down)
  }
  def addToRequests(request:Request) = requests = requests + request
  def hasRequestsForLowerFloors(floor:Int) = { requests.exists( _.floor < floor)}
  def hasRequestsForHigherFloors(floor:Int) = { requests.exists( _.floor > floor)}
  def removeAllRequestsForFloor(floor:Int) = requests = requests.filter(_.floor != floor) //TODO do this without side effects, return the result of the filter, pass in the requests object
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

  case class Data(direction:Direction, currentFloor:Int)
}

object ElevatorProtocol {
  import ElevatorActor._

  sealed trait Request {
    val floor:Int
  }
  case class GetOn(floor:Int, direction:Direction) extends Request
  case class GetOff(floor:Int) extends Request

  case object GetRequests
  case object GetCurrentFloor
  case class ArrivedAtFloor(floor:Int)
}
