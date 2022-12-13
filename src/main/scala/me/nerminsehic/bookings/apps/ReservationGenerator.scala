package me.nerminsehic.bookings.apps

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import me.nerminsehic.bookings.actor.{Hotel, ReservationAgent}
import me.nerminsehic.bookings.model.{Generate, ManageHotel}

object ReservationGenerator {
  def main(args: Array[String]): Unit = {
    val root = Behaviors.setup[String] { context =>
      val agent = context.spawn(ReservationAgent(), "agent")
      val hotels = (1 to 100)
        .map(i => s"hotel_$i")
        .map(hotelId => context.spawn(Hotel(hotelId), hotelId))

      hotels.foreach(agent ! ManageHotel(_))

      // generate events in bursts
      (1 to 100).foreach { _ =>
        agent ! Generate(10)
        Thread.sleep(100)
      }

      Behaviors.empty
    }

    val system = ActorSystem(root, "ReservationGenerator")
  }

}
