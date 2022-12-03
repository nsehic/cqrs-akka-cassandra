package me.nerminsehic.bookings.playground.actor

import akka.actor.typed.Behavior
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import me.nerminsehic.bookings.playground.model._

object Hotel {
  case class State(reservations: Set[Reservation])

  def commandHandler(hotelId: String): (State, Command) => Effect[Event, State] = (state, command) =>
    command match {
      case MakeReservation(guestId, startDate, endDate, roomNumber, replyTo) =>
        // persist ReservationAccepted
        val tentativeReservation = Reservation.make(guestId, hotelId, startDate, endDate, roomNumber)
        val conflictingReservation: Option[Reservation] = state.reservations.find(r => r.intersect(tentativeReservation))

        if(conflictingReservation.isEmpty) {
          Effect
            .persist(ReservationAccepted(tentativeReservation)) // persist
            .thenReply(replyTo)(s => ReservationAccepted(tentativeReservation)) // reply to the "manager"
        } else {
          Effect.reply(replyTo)(CommandFailure("Reservation failed: Conflict with another reservation"))
        }

      case ChangeReservation(confirmationNumber, startDate, endDate, roomNumber, replyTo) =>
        Effect.none // TODO

      case CancelReservation(confirmationNumber, replyTo) =>
        Effect.none // TODO

    }

  def eventHandler(hotelId: String): (State, Event) => State = (state, event) =>
    event match {
      case ReservationAccepted(res) =>
        val newState = state.copy(reservations = state.reservations + res)
        println("state changed: $newState")
        newState

      case _ =>
        state // TODO
    }

  def apply(hotelId: String): Behavior[Command] =
    EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId(hotelId),
      emptyState = State(Set()),
      commandHandler = commandHandler(hotelId),
      eventHandler = eventHandler(hotelId)
    )
}
