package com.tombray.examples.akka

import akka.actor.FSM.{Transition, CurrentState, SubscribeTransitionCallBack}
import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestProbe, TestActorRef, ImplicitSender, TestKit}
import com.tombray.examples.akka.ElevatorActor._
import com.tombray.examples.akka.ElevatorProtocol._
import org.scalatest.{FunSpecLike, MustMatchers}
import scala.concurrent.duration._

/**
 * Created by tbray on 7/24/14.
 */
class ElevatorActorSpec extends TestKit(ActorSystem("test-system"))
with MustMatchers
with FunSpecLike
with StopSystemAfterAll
with ImplicitSender {

  describe("An elevator") {
    //TODO refactor using TestFSMRef throughout
    it("should allow setting and getting the current floor manually") {
      val elevator = TestActorRef(Props(new ElevatorActor))
      elevator ! ArrivedAtFloor(10)
      elevator ! GetCurrentFloor
      expectMsg(10)
    }

    it("should transition to Open when it receives a request to get on at its current floor") {
      val elevator = TestActorRef(Props(new ElevatorActor))
      elevator ! ArrivedAtFloor(10)
      elevator ! SubscribeTransitionCallBack(testActor)

      expectMsgPF() {
        case CurrentState(elevator,Idle) => true
      }

      elevator ! GetOn(10, Down)

      expectMsg(Transition(elevator, Idle, Open))
    }

    it("should transition to GoingDown when it receives a request to GetOn from a lower floor") {
      val elevator = TestActorRef(Props(new ElevatorActor))

      elevator ! ArrivedAtFloor(10)
      elevator ! SubscribeTransitionCallBack(testActor)

      expectMsgPF() {
        case CurrentState(_,Idle) => true
      }

      elevator ! GetOn(0, Up)

      expectMsg(Transition(elevator, Idle, GoingDown))
    }

    it("should transition to GoingUp when it it receives a request to GetOn from a higher floor") {
      val elevator = TestActorRef(Props(new ElevatorActor))

      elevator ! ArrivedAtFloor(0)

      elevator ! SubscribeTransitionCallBack(testActor)

      expectMsgPF() {
        case CurrentState(_,Idle) => true
      }

      elevator ! GetOn(10, Down)

      expectMsg(Transition(elevator, Idle, GoingUp))
    }

    it("should return current requests") {
      val elevator = TestActorRef(Props(new ElevatorActor))
      val probe = TestProbe()
      elevator ! ArrivedAtFloor(10)
      elevator ! GetOn(0, Up)
      elevator ! GetOff(3)
      elevator ! GetOn(1, Down)
      elevator.tell(GetRequests, probe.ref)
      probe.expectMsg(Set(GetOn(0, Up), GetOn(1, Down), GetOff(3)))
    }

    it("should stop on the floor it was called from and Open") {
      val elevator = TestActorRef(Props(new ElevatorActor))
      elevator ! SubscribeTransitionCallBack(testActor)

      expectMsgPF() {
        case CurrentState(_,Idle) => true
      }

      elevator ! ArrivedAtFloor(2)
      elevator ! GetOn(0, Up)

      expectMsg(Transition(elevator, Idle, GoingDown))

      elevator ! ArrivedAtFloor(0)

      expectMsg(Transition(elevator, GoingDown, Open))
    }

    it("should transition from Open back to GoingDown if called from more than one lower floor") {
      val elevator = TestActorRef(Props(new ElevatorActor))
      elevator ! SubscribeTransitionCallBack(testActor)

      expectMsgPF() {
        case CurrentState(_,Idle) => true
      }

      elevator ! ArrivedAtFloor(2)
      elevator ! GetOn(0, Up)
      elevator ! GetOn(1, Down)

      expectMsg(Transition(elevator, Idle, GoingDown))

      elevator ! ArrivedAtFloor(1)

      expectMsg(Transition(elevator, GoingDown, Open))

      within(4 seconds) {
        expectMsg(Transition(elevator, Open, GoingDown))
      }

      val probe = TestProbe()

      //request to GetOn(1, Down) should be removed
      elevator.tell(GetRequests, probe.ref)
      probe.expectMsg(Set(GetOn(0, Up)))

      elevator ! ArrivedAtFloor(0)

      expectMsg(Transition(elevator, GoingDown, Open))

      //request to GetOn(0, Up) should be removed
      elevator.tell(GetRequests, probe.ref)
      probe.expectMsg(Set())

    }
    it("should transition from Open to GoingDown if there are requests to get off at a lower floor") {
      val elevator = TestActorRef(Props(new ElevatorActor))
      elevator ! SubscribeTransitionCallBack(testActor)

      expectMsgPF() {
        case CurrentState(_,Idle) => true
      }

      elevator ! ArrivedAtFloor(3)
      elevator ! GetOn(3,Down)

      expectMsg(Transition(elevator, Idle, Open))

      elevator ! GetOff(0)
      elevator ! GetOff(1)

      within(4 seconds) {
        expectMsg(Transition(elevator, Open, GoingDown))
      }

      elevator ! ArrivedAtFloor(1)

      expectMsg(Transition(elevator, GoingDown, Open))

      within(4 seconds) {
        expectMsg(Transition(elevator, Open, GoingDown))
      }

      val probe = TestProbe()

      //request to GetOn(1, Down) should be removed
      elevator.tell(GetRequests, probe.ref)
      probe.expectMsg(Set(GetOff(0)))

      elevator ! ArrivedAtFloor(0)

      expectMsg(Transition(elevator, GoingDown, Open))

      //request to GetOn(0, Up) should be removed
      elevator.tell(GetRequests, probe.ref)
      probe.expectMsg(Set())

    }

    it("should go down to lowest requested floor then switch to GoingUp to pick up higher passenger") {
      val elevator = TestActorRef(Props(new ElevatorActor))
      elevator ! SubscribeTransitionCallBack(testActor)

      expectMsgPF() {
        case CurrentState(_,Idle) => true
      }

      elevator ! ArrivedAtFloor(3)
      elevator ! GetOn(3,Down)

      expectMsg(Transition(elevator, Idle, Open))

      elevator ! GetOff(0)
      elevator ! GetOff(1)

      within(4 seconds) {
        expectMsg(Transition(elevator, Open, GoingDown))
      }

      elevator ! ArrivedAtFloor(1)

      elevator ! GetOn(3,Down)

      expectMsg(Transition(elevator, GoingDown, Open))

      within(4 seconds) {
        expectMsg(Transition(elevator, Open, GoingDown))
      }

      val probe = TestProbe()

      //request to GetOn(1, Down) should be removed
      elevator.tell(GetRequests, probe.ref)
      probe.expectMsg(Set(GetOff(0),GetOn(3,Down)))

      elevator ! ArrivedAtFloor(0)

      expectMsg(Transition(elevator, GoingDown, Open))

      //request to GetOn(0, Up) should be removed
      elevator.tell(GetRequests, probe.ref)
      probe.expectMsg(Set(GetOn(3,Down)))

      within(4 seconds) {
        expectMsg(Transition(elevator, Open, GoingUp))
      }

      elevator ! ArrivedAtFloor(3)

      expectMsg(Transition(elevator, GoingUp, Open))

      elevator ! GetOff(0)

      within(4 seconds) {
        expectMsg(Transition(elevator, Open, GoingDown))
      }
    }
  }


}
