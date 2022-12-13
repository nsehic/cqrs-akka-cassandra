package me.nerminsehic.bookings.apps

import akka.{Done, NotUsed}
import akka.actor.typed.ActorSystem
import akka.actor.typed.javadsl.Behaviors
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.alpakka.cassandra.scaladsl.CassandraSessionRegistry
import akka.stream.scaladsl.{Sink, Source}
import me.nerminsehic.bookings.model.{Reservation, ReservationAccepted, ReservationCancelled, ReservationUpdated}

import java.time.temporal.ChronoUnit
import scala.concurrent.Future

object HotelEventReader {
  implicit val system: ActorSystem[_] = ActorSystem(Behaviors.empty, "HotelEventReaderSystem")
  import system.executionContext

  private val readJournal = PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  // Cassandra Session
  private val session = CassandraSessionRegistry(system).sessionFor("akka.projection.cassandra.session-config")

  // all persistence ids
  private val persistenceIds: Source[String, NotUsed] = readJournal.persistenceIds()
  private val consumptionSink: Sink[Any, Future[Done]] = Sink.foreach(println)
  private val connectedGraph = persistenceIds.to(consumptionSink)

  def makeReservation(reservation: Reservation): Future[Unit] = {
    val Reservation(guestId, hotelId, startDate, endDate, roomNumber, confirmationNumber) = reservation
    val startLocalDate = startDate.toLocalDate
    val endLocalDate = endDate.toLocalDate
    val daysBlocked = startLocalDate.until(endLocalDate, ChronoUnit.DAYS).toInt

    val blockedDaysFutures = for {
      days <- 0 until daysBlocked
    } yield session.executeWrite(
      "UPDATE hotel.available_rooms_by_hotel_date SET is_available = false WHERE " +
        s"hotel_id='$hotelId' and date='${startLocalDate.plusDays(days)}' and room_number=$roomNumber"
    ).recover(e => println(s"Room day blocking failed: ${e}"))

    val reservationGuestDateFuture = session.executeWrite(
      "INSERT INTO reservation.reservations_by_hotel_date (hotel_id, start_date, end_date, room_number, confirm_number, guest_id) VALUES " +
        s"('$hotelId', '$startDate', '$endDate', $roomNumber, '$confirmationNumber', $guestId)"
    ).recover(e => println(s"Reservation for date failed: ${e}"))

    val reservationGuestFuture = session.executeWrite(
      "INSERT INTO reservation.reservations_by_guest (guest_last_name, hotel_id, start_date, end_date, room_number, confirm_number, guest_id) VALUES " +
        s"('Sehic', '$hotelId', '$startDate', '$endDate', $roomNumber, '$confirmationNumber', $guestId)"
    ).recover(e => println(s"reservation for guest failed: ${e}"))

    Future.sequence(reservationGuestFuture :: reservationGuestDateFuture :: blockedDaysFutures.toList).map(_ => ())
  }

  def removeReservation(reservation: Reservation): Future[Unit] = {
    val Reservation(guestId, hotelId, startDate, endDate, roomNumber, confirmationNumber) = reservation
    val startLocalDate = startDate.toLocalDate
    val endLocalDate = endDate.toLocalDate
    val daysBlocked = startLocalDate.until(endLocalDate, ChronoUnit.DAYS).toInt

    val blockedDaysFutures = for {
      days <- 0 until daysBlocked
    } yield session.executeWrite(
      "UPDATE hotel.available_rooms_by_hotel_date SET is_available = true WHERE " +
        s"hotel_id='$hotelId' and date='${startLocalDate.plusDays(days)}' and room_number=$roomNumber"
    ).recover(e => println(s"Room day unblocking failed: ${e}"))

    val reservationGuestDateFuture = session.executeWrite(
      "DELETE FROM reservation.reservations_by_hotel_date WHERE " +
        s"hotel_id='$hotelId' and start_date='$startDate' and room_number=$roomNumber"
    ).recover(e => println(s"Reservation removal for date failed: ${e}"))

    val reservationGuestFuture = session.executeWrite(
      "DELETE FROM reservation.reservations_by_guest WHERE " +
        s"guest_last_name='Sehic' and confirm_number='$confirmationNumber'"
    ).recover(e => println(s"Reservation removal for guest failed: ${e}"))

    Future.sequence(reservationGuestFuture :: reservationGuestDateFuture :: blockedDaysFutures.toList).map(_ => ())
  }

  // all events for a persistence ID
  private val eventsForTestHotel = readJournal
    .eventsByPersistenceId("hotel_37", 0, Long.MaxValue)
    .map(_.event)
    .mapAsync(8) {
      case ReservationAccepted(res) =>
        println(s"MAKING RESERVATION: $res")
        makeReservation(res)
      case ReservationUpdated(oldReservation, newReservation) =>
        println(s"CHANGING RESERVATION: from $oldReservation to $newReservation")
        for {
          _ <- removeReservation(oldReservation)
          _ <- makeReservation(newReservation)
        } yield ()
      case ReservationCancelled(res) =>
        println(s"CANCELLING RESERVATION: $res")
        removeReservation(res)
    }
  def main(args: Array[String]): Unit = {
    eventsForTestHotel.to(Sink.ignore).run()
  }
}
