#Akka FSM Examples
I really fell in love with Akka when I discovered its Finite State Machine DSL. I created this project to provide examples of how to 
use the FSM trait with your actors and how to test your state machines. One of the classic examples of a state machine is an elevator, 
so I thought I'd start there. I developed my Elevator test-first which allowed me to discover a nice design and to be confident as I refactored.
What I ended up with is a fully functioning elevator that responds to requests to get on and off in a pretty efficient manner -- certainly more efficient
than the real elevators in the building where I work! And it's just around 100 lines of code!

Please start by taking a look at the elevator itself: [ElevatorActor.scala](https://github.com/tombray/akka-fsm-examples/blob/master/src/main/scala/com/tombray/examples/akka/ElevatorActor.scala)

And then walk through the tests that I wrote as I fleshed out the behavior: [ElevatorActorSpec.scala](https://github.com/tombray/akka-fsm-examples/blob/master/src/test/scala/com/tombray/examples/akka/ElevatorActorSpec.scala)

Soon, I'll add an ElevatorDispatcherActor to manage multiple elevators in the same building and determine the best elevator to service incoming requests.