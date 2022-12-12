package me.nerminsehic.bookings.actor

import akka.actor.typed.Behavior
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import me.nerminsehic.bookings.model._

object Hotel {
  case class State(reservations: Set[Reservation])

  def commandHandler(hotelId: String): (State, Command) => Effect[Event, State] = (state, command) =>
    command match {
      case MakeReservation(guestId, startDate, endDate, roomNumber, replyTo) =>
        // persist ReservationAccepted
        val tentativeReservation = Reservation.make(guestId, hotelId, startDate, endDate, roomNumber)
        val conflictingReservation: Option[Reservation] = state.reservations.find(r => r.intersect(tentativeReservation))

        if (conflictingReservation.isEmpty) {
          Effect
            .persist(ReservationAccepted(tentativeReservation)) // persist
            .thenReply(replyTo)(s => ReservationAccepted(tentativeReservation)) // reply to the "manager"
        } else {
          Effect.reply(replyTo)(CommandFailure("Reservation failed: Conflict with another reservation"))
        }

      case ChangeReservation(confirmationNumber, startDate, endDate, roomNumber, replyTo) =>
        val oldReservationOption = state.reservations.find(_.confirmationNumber == confirmationNumber)
        val newReservationOption = oldReservationOption
          .map(res => res.copy(startDate = startDate, endDate = endDate, roomNumber = roomNumber))
        val reservationUpdatedEventOption = oldReservationOption.zip(newReservationOption)
          .map(ReservationUpdated.tupled)
        val conflictingReservationOption = newReservationOption.flatMap { tentativeReservation =>
          state.reservations.find(r => r.confirmationNumber != confirmationNumber && r.intersect(tentativeReservation))
        }

        (reservationUpdatedEventOption, conflictingReservationOption) match {
          case (None, _) =>
            Effect.reply(replyTo)(CommandFailure(s"Cannot update reservation $confirmationNumber: not found"))
          case (_, Some(_)) =>
            Effect.reply(replyTo)(CommandFailure(s"Cannot update reservation $confirmationNumber: conflicting reservation"))
          case (Some(resUpdated), None) =>
            Effect.persist(resUpdated).thenReply(replyTo)((s: State) => resUpdated)
        }

      case CancelReservation(confirmationNumber, replyTo) =>
        val reservationOption = state.reservations.find(_.confirmationNumber == confirmationNumber)
        reservationOption match {
          case Some(res) =>
            Effect.persist(ReservationCancelled(res)).thenReply(replyTo)(s => ReservationCancelled(res))
          case None =>
            Effect.reply(replyTo)(CommandFailure(s"Cannot cancel reservation $confirmationNumber: not found"))
        }

    }

  def eventHandler(hotelId: String): (State, Event) => State = (state, event) =>
    event match {
      case ReservationAccepted(res) =>
        val newState = state.copy(reservations = state.reservations + res)
        println(s"state changed: $newState")
        newState
      case ReservationUpdated(oldReservation, newReservation) =>
        val newState = state.copy(reservations = state.reservations - oldReservation + newReservation)
        println(s"state changed: $newState")
        newState
      case ReservationCancelled(res) =>
        val newState = state.copy(reservations = state.reservations - res)
        println(s"state changed: $newState")
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
