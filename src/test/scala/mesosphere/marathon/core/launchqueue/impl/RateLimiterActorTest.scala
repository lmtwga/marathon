package mesosphere.marathon.core.launchqueue.impl

import akka.actor.{ ActorRef, ActorSystem }
import akka.pattern.ask
import akka.testkit.TestProbe
import akka.util.Timeout
import mesosphere.marathon.core.base.ConstantClock
import mesosphere.marathon.test.MarathonSpec
import mesosphere.marathon.core.launchqueue.LaunchQueueConfig
import mesosphere.marathon.core.task.tracker.InstanceTracker
import mesosphere.marathon.state.{ AppDefinition, BackoffStrategy, PathId }
import mesosphere.marathon.test.MarathonSpec
import org.mockito.Mockito

import scala.concurrent.Await
import scala.concurrent.duration._

class RateLimiterActorTest extends MarathonSpec {

  test("GetDelay gets current delay") {
    rateLimiter.addDelay(app)

    val delay = askLimiter(RateLimiterActor.GetDelay(app)).asInstanceOf[RateLimiterActor.DelayUpdate]
    assert(delay.delayUntil == clock.now() + backoff)
  }

  test("AddDelay increases delay and sends update") {
    limiterRef ! RateLimiterActor.AddDelay(app)
    updateReceiver.expectMsg(RateLimiterActor.DelayUpdate(app, clock.now() + backoff))
    val delay = askLimiter(RateLimiterActor.GetDelay(app)).asInstanceOf[RateLimiterActor.DelayUpdate]
    assert(delay.delayUntil == clock.now() + backoff)
  }

  test("ResetDelay resets delay and sends update") {
    limiterRef ! RateLimiterActor.AddDelay(app)
    updateReceiver.expectMsg(RateLimiterActor.DelayUpdate(app, clock.now() + backoff))
    limiterRef ! RateLimiterActor.ResetDelay(app)
    updateReceiver.expectMsg(RateLimiterActor.DelayUpdate(app, clock.now()))
    val delay = askLimiter(RateLimiterActor.GetDelay(app)).asInstanceOf[RateLimiterActor.DelayUpdate]
    assert(delay.delayUntil == clock.now())
  }

  private[this] def askLimiter(message: Any): Any = {
    Await.result(limiterRef ? message, 3.seconds)
  }

  private val backoff = 10.seconds
  private val backoffStrategy = BackoffStrategy(backoff = backoff, factor = 2.0)
  private[this] val app = AppDefinition(id = PathId("/test"), backoffStrategy = backoffStrategy)

  private[this] implicit val timeout: Timeout = 3.seconds
  private[this] implicit var actorSystem: ActorSystem = _
  private[this] var launchQueueConfig: LaunchQueueConfig = _
  private[this] var clock: ConstantClock = _
  private[this] var rateLimiter: RateLimiter = _
  private[this] var taskTracker: InstanceTracker = _
  private[this] var updateReceiver: TestProbe = _
  private[this] var limiterRef: ActorRef = _

  before {
    actorSystem = ActorSystem()
    launchQueueConfig = new LaunchQueueConfig {
      verify()
    }
    clock = ConstantClock()
    rateLimiter = Mockito.spy(new RateLimiter(launchQueueConfig, clock))
    taskTracker = mock[InstanceTracker]
    updateReceiver = TestProbe()
    val props = RateLimiterActor.props(rateLimiter, updateReceiver.ref)
    limiterRef = actorSystem.actorOf(props, "limiter")
  }

  after {
    Await.result(actorSystem.terminate(), Duration.Inf)
  }
}
