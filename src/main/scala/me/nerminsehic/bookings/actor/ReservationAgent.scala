package me.nerminsehic.bookings.actor

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import me.nerminsehic.bookings.model.{CancelReservation, ChangeReservation, Command, Generate, HotelProtocol, MakeReservation, ManageHotel, Reservation, ReservationAccepted, ReservationCancelled, ReservationUpdated}

import java.sql.Date
import java.time.LocalDate
import java.util.UUID
import scala.util.Random

object ReservationAgent {
  def apply(): Behavior[HotelProtocol] = active(Vector(), Map())


  private val DELETE_PROB = 0.05
  private val CHANGE_PROB = 0.2
  private val START_DATE = LocalDate.of(2023, 1, 1)

  private def generateAndSend(hotels: Vector[ActorRef[Command]], state: Map[String, Reservation])(implicit replyTo: ActorRef[HotelProtocol]): Unit = {
    val prob = Random.nextDouble()
    if(prob <= DELETE_PROB && state.keys.nonEmpty) {
      // generate cancellation
      val confNumbers = state.keysIterator.toVector
      val confNumberIndex = Random.nextInt(confNumbers.size)
      val confNumber = confNumbers(confNumberIndex)
      val reservation = state(confNumber)
      val hotel = hotels.find(_.path.name == reservation.hotelId)
      hotel.foreach(_ ! CancelReservation(confNumber, replyTo))
    } else if(prob <= CHANGE_PROB && state.keys.nonEmpty) {
        // generate a reservation change
      val confNumbers = state.keysIterator.toVector
      val confNumberIndex = Random.nextInt(confNumbers.size)
      val confNumber = confNumbers(confNumberIndex)
      val Reservation(guestId, hotelId, startDate, endDate, roomNumber, confirmationNumber) = state(confNumber)
      val localStart = startDate.toLocalDate
      val localEnd = endDate.toLocalDate

      // can change duration or room number
      val isDurationChange = Random.nextBoolean()
      val newReservation =
        if(isDurationChange) {
          val newLocalStart = localStart.plusDays(Random.nextInt(5) - 2) // between -2 and +3 days
          val tentativeLocalEnd = localEnd.plusDays(Random.nextInt(5) - 2) // same, but interval might be degenerate
          val newLocalEnd =
            if(tentativeLocalEnd.compareTo(newLocalStart) <= 0)
              newLocalStart.plusDays(Random.nextInt(5) + 1)
            else
              tentativeLocalEnd

          Reservation(guestId, hotelId, Date.valueOf(newLocalStart), Date.valueOf(newLocalEnd), roomNumber, confirmationNumber)
        } else {
          val newRoomNumber = Random.nextInt(100) + 1
          Reservation(guestId, hotelId, startDate, endDate, newRoomNumber, confirmationNumber)
        }

      val Reservation(_, _, newStartDate, newEndDate, newRoomNumber, _) = newReservation
      val hotel = hotels.find(_.path.name == hotelId)

      // take both changes into account in a single call
      hotel.foreach(_ ! ChangeReservation(confirmationNumber, newStartDate, newEndDate, newRoomNumber, replyTo))
    } else {
      // new reservation
      val hotelIndex = Random.nextInt(hotels.size)
      val startDate = START_DATE.plusDays(Random.nextInt(365))
      val endDate = startDate.plusDays(Random.nextInt(14))
      val roomNumber = 1 + Random.nextInt(100)
      val hotel = hotels(hotelIndex)
      hotel ! MakeReservation(UUID.randomUUID().toString, Date.valueOf(startDate), Date.valueOf(endDate), roomNumber, replyTo)
    }
  }


  def active(hotels: Vector[ActorRef[Command]], state: Map[String, Reservation]): Behavior[HotelProtocol] =
    Behaviors.receive[HotelProtocol] { (context, message) =>
      implicit val self: ActorRef[HotelProtocol] = context.self

      message match {
        case Generate(nCommands) =>
          (1 to nCommands).foreach(_ => generateAndSend(hotels, state))
          Behaviors.same
        case ManageHotel(hotel) =>
          context.log.info(s"Managing hotel ${hotel.path.name}")
          active(hotels :+ hotel, state)
        case ReservationAccepted(reservation) =>
          context.log.info(s"Reservation accepted: ${reservation.confirmationNumber}")
          active(hotels, state + (reservation.confirmationNumber -> reservation))
        case ReservationUpdated(_, newReservation) =>
          context.log.info(s"Reservation updated: ${newReservation.confirmationNumber}")
          active(hotels, state + (newReservation.confirmationNumber -> newReservation))
        case ReservationCancelled(reservation) =>
          context.log.info(s"Reservation cancelled: ${reservation.confirmationNumber}")
          active(hotels, state - reservation.confirmationNumber)
        case _ =>
          Behaviors.same
      }
    }

}
