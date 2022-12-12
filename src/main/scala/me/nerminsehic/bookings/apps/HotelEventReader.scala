package me.nerminsehic.bookings.apps

import akka.{Done, NotUsed}
import akka.actor.typed.ActorSystem
import akka.actor.typed.javadsl.Behaviors
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.scaladsl.{Sink, Source}
import me.nerminsehic.bookings.model.{ReservationAccepted, ReservationCancelled, ReservationUpdated}

import scala.concurrent.Future

object HotelEventReader {
  implicit val system: ActorSystem[_] = ActorSystem(Behaviors.empty, "HotelEventReaderSystem")

  private val readJournal = PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  // all persistence ids
  private val persistenceIds: Source[String, NotUsed] = readJournal.persistenceIds()
  private val consumptionSink: Sink[Any, Future[Done]] = Sink.foreach(println)
  private val connectedGraph = persistenceIds.to(consumptionSink)

  // all events for a persistence ID
  private val eventsForTestHotel = readJournal
    .eventsByPersistenceId("testHotel", 0, Long.MaxValue)
    .map(_.event)
    .map {
      case ReservationAccepted(res) =>
        println(s"MAKING RESERVATION: $res")
      case ReservationUpdated(oldReservation, newReservation) =>
        println(s"CHANGING RESERVATION: from $oldReservation to $newReservation")
      case ReservationCancelled(res) =>
        println(s"CANCELLING RESERVATION: $res")
    }
  def main(args: Array[String]): Unit = {
    eventsForTestHotel.to(Sink.ignore).run()
  }
}
