package me.nerminsehic.bookings.model

import akka.actor.typed.ActorRef
import java.sql.Date

// commands
sealed trait Command
case class MakeReservation(guestId: String, startDate: Date, endDate: Date, roomNumber: Int, replyTo: ActorRef[Any]) extends Command
case class ChangeReservation(confirmationNumber: String, startDate: Date, endDate: Date, roomNumber: Int, replyTo: ActorRef[Any]) extends Command
case class CancelReservation(confirmationNumber: String, replyTo: ActorRef[Any]) extends Command

// events
sealed trait Event
case class ReservationAccepted(reservation: Reservation) extends Event
case class ReservationUpdated(oldReservation: Reservation, newReservation: Reservation) extends Event
case class ReservationCancelled(reservation: Reservation) extends Event

// communication with the agent
case class CommandFailure(reason: String)