package me.nerminsehic.bookings.apps

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import me.nerminsehic.bookings.actor.Hotel
import me.nerminsehic.bookings.model.{CancelReservation, MakeReservation}

import java.sql.Date
import java.util.UUID
import scala.concurrent.duration._

object HotelDemo {
  def main(args: Array[String]): Unit = {
    val simpleLogger = Behaviors.receive[Any] { (ctx, message) =>
      ctx.log.info(s"[logger] $message")
      Behaviors.same
    }

    val root = Behaviors.setup[String] { ctx =>
      val logger = ctx.spawn(simpleLogger, "logger") // child actor
      val hotel = ctx.spawn(Hotel("testHotel"), "testHotel")

//      hotel ! MakeReservation(UUID.randomUUID().toString, Date.valueOf("2022-12-03"), Date.valueOf("2022-12-10"), 101, logger)
//      hotel ! ChangeReservation("B5NJGQ9NNN", Date.valueOf("2022-12-03"), Date.valueOf("2022-12-14"), 101, logger)
      hotel ! CancelReservation("CJ10TLSMZ1", logger)
      Behaviors.empty
    }

    val system = ActorSystem(root, "DemoHotel")
    import system.executionContext
    system.scheduler.scheduleOnce(5.seconds, () => system.terminate())
  }
}
