/*
 * Copyright 2012 Pellucid and Zenexity
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package datomisca

import scala.language.higherKinds
import language.experimental.macros

import scala.util.{Try, Success, Failure}
import scala.util.parsing.input.Positional

import dmacros._

trait DatomicQueryExecutor extends QueryExecutorPure with QueryExecutorAuto

/* DATOMIC QUERY */
trait Query {
  def find: Find
  def wizz: Option[With] = None
  def in: Option[In] = None
  def where: Where

  override def toString = s"""[ $find ${wizz.map( _.toString + " " ).getOrElse("")}${in.map( _.toString + " " ).getOrElse("")}$where ]"""
}

object Query extends QueryMacros {
  def apply(find: Find, where: Where): PureQuery = PureQuery(find, None, None, where)
  def apply(find: Find, in: In, where: Where): PureQuery = PureQuery(find, None, Some(in), where)
  def apply(find: Find, in: Option[In], where: Where): PureQuery = PureQuery(find, None, in, where)
  def apply(find: Find, wizz: With, in: In, where: Where): PureQuery = PureQuery(find, Some(wizz), Some(in), where)
  def apply(find: Find, wizz: Option[With], in: Option[In], where: Where): PureQuery = PureQuery(find, wizz, in, where)
}


trait QueryMacros {
  /** Creates a macro-based compile-time pure query from a String (only syntax validation is performed).<br/>
    * '''Keep in mind a query is an immutable data structure that you can manipulate'''
    *
    *     - A [[PureQuery]] is the low-level query AST provided in the Scala API.
    *     - [[TypedQuery]] is based on it.
    *     - When a [[PureQuery]] is executed, it returns a `List[List[DatomicData]].
    *     - All returned types are [[DatomicData]].
    *
    * {{{
    * // creates a query
    * val q = Datomic.Query.pure("""
    *  [
    *    :find ?e ?name
    *    :in $ ?char
    *    :where  [ ?e :person/name ?name ]
    *            [ ?e :person/character ?char ]
    *  ]
    * """)
    *
    * Datomic.query(q, database, person.character / "violent") map {
    *   case List(DLong(e), DString(name)) =>
    *     ...
    * }
    * }}}
    *
    * @param q the query String
    * @return a PureQuery
    */
  def pure(q: String): PureQuery = macro DatomicQueryMacro.pureQueryImpl

  /** Creates a macro-based compile-time typed query from a String:
    *    - syntax validation is performed.
    *    - determines number of input(M) parameters
    *    - determines number of output(N) parameters
    *    - creates a TypedQueryN[DatomicData, ...M-times, TupleN[DatomicData, ...N-times]]
    *
    * '''Keep in mind a query is an immutable data structure that you can manipulate'''
    *
    * When a [[TypedQuery]] is executed, it returns a `List[TupleN[DatomicData, DatomicData, ...]]` where X corresponds
    * to the number of output parameters
    *
    * {{{
    * val q = Datomic.Query.auto("""
    *   [
    *    :find ?e ?name ?age
    *    :in $ [[?name ?age]]
    *    :where [?e :person/name ?name]
    *           [?e :person/age ?age]
    *   ]
    * """)
    *
    * Datomic.q(
    *   q, database,
    *   DColl(
    *     Datomic.coll("toto", 30L),
    *     Datomic.coll("tutu", 54L)
    *   )
    * ) map {
    *   case (DLong(e), DString(n), DLong(a)) =>
    *      ...
    * }
    * }}}
    */
  def auto(q: String) = macro DatomicQueryMacro.autoTypedQueryImpl
  def apply(q: String) = macro DatomicQueryMacro.autoTypedQueryImpl


  /** Macro-based helper to create Rule alias to be used in Queries.
    * {{{
    * val totoRule = Datomic.Query.rules("""
    *   [[[toto ?e]
    *     [?e :person/name "toto"]
    *   ]]
    * """)
    *
    * val q = Datomic.typedQuery[Args2, Args2]("""
    *   [
    *     :find ?e ?age
    *     :in $ %
    *     :where [?e :person/age ?age]
    *            (toto ?e)
    *   ]
    * """)
    *
    * Datomic.query(q, database, totoRule) map {
    *   case (DLong(e), DLong(age)) =>
    *     ...
    * }
    * }}}
    */
  def rules(q: String): DRuleAliases = macro DatomicQueryMacro.rulesImpl
}

case class PureQuery(override val find: Find, override val wizz: Option[With] = None, override val in: Option[In] = None, override val where: Where) extends Query

abstract class TypedQueryAuto(query: PureQuery) extends Query {
  override def find = query.find
  override def wizz = query.wizz
  override def in = query.in
  override def where = query.where
}

case class TypedQueryAuto0[R](query: PureQuery) extends TypedQueryAuto(query)
case class TypedQueryAuto1[A, R](query: PureQuery) extends TypedQueryAuto(query)
case class TypedQueryAuto2[A, B, R](query: PureQuery) extends TypedQueryAuto(query)
case class TypedQueryAuto3[A, B, C, R](query: PureQuery) extends TypedQueryAuto(query)
case class TypedQueryAuto4[A, B, C, D, R](query: PureQuery) extends TypedQueryAuto(query)
case class TypedQueryAuto5[A, B, C, D, E, R](query: PureQuery) extends TypedQueryAuto(query)
case class TypedQueryAuto6[A, B, C, D, E, F, R](query: PureQuery) extends TypedQueryAuto(query)
case class TypedQueryAuto7[A, B, C, D, E, F, G, R](query: PureQuery) extends TypedQueryAuto(query)
case class TypedQueryAuto8[A, B, C, D, E, F, G, H, R](query: PureQuery) extends TypedQueryAuto(query)


/* DATOMIC QUERY */
object QueryExecutor {
  import scala.collection.JavaConverters._

  private[datomisca] def directQuery(q: Query, in: Seq[AnyRef]) =
    new Iterable[IndexedSeq[DatomicData]] {
      private val jColl: java.util.Collection[java.util.List[AnyRef]] = datomic.Peer.q(q.toString, in: _*)
      override def iterator = new Iterator[IndexedSeq[DatomicData]] {
        private val jIter: java.util.Iterator[java.util.List[AnyRef]] = jColl.iterator
        override def hasNext = jIter.hasNext
        override def next() = new IndexedSeq[DatomicData] {
          private val jList: java.util.List[AnyRef] = jIter.next()
          override def length = jList.size
          override def apply(idx: Int): DatomicData =
            Datomic.toDatomicData(jList.get(idx))
          override def iterator = new Iterator[DatomicData] {
            private val jIter: java.util.Iterator[AnyRef] = jList.iterator
            override def hasNext = jIter.hasNext
            override def next() = Datomic.toDatomicData(jIter.next)
          }
        }
      }
    }

  private[datomisca] def directQueryOut[OutArgs](q: Query, in: Seq[AnyRef])(implicit outConv: DatomicDataToArgs[OutArgs]): List[OutArgs] = {
    import scala.collection.JavaConversions._
    import scala.collection.JavaConverters._

    // serializes query
    val qser = q.toString

    //println("QSER:"+qser)

    val args = in

    val results: List[List[Any]] = datomic.Peer.q(qser, args: _*).toList.map(_.toList)

    val listOfTry = results.map { fields =>
      outConv.toArgs(fields.map { field => Datomic.toDatomicData(field) })
    }

    listOfTry.foldLeft(Nil: List[OutArgs]){ (acc, e) => acc :+ e }
  }
}

trait QueryExecutorPure {
  def q(query: PureQuery, in: DatomicData*): Iterable[IndexedSeq[DatomicData]] =
    QueryExecutor.directQuery(query, in.map(_.toNative))
}


trait QueryExecutorAuto extends ToDatomicCastImplicits{
  /*def q[R](query: TypedQueryAuto0[R])(
    implicit outConv: DatomicDataToArgs[R], db: DDatabase
  ): List[R] = QueryExecutor.directQueryOut[R](query, Seq(db))(outConv)*/

  def q[R](query: TypedQueryAuto0[R], dataSource: DatomicData)(
    implicit outConv: DatomicDataToArgs[R]
  ): List[R] = QueryExecutor.directQueryOut[R](query, Seq(dataSource.toNative))(outConv)

  def q[A, R](query: TypedQueryAuto1[A, R], a: A)(
    implicit tdata: ToDatomicCast[A],
             outConv: DatomicDataToArgs[R]
  ): List[R] = QueryExecutor.directQueryOut[R](query, Seq(tdata.to(a).toNative))(outConv)

  def q[A, B, R](query: TypedQueryAuto2[A, B, R], a: A, b: B)(
    implicit tdata: ToDatomicCast[A], tdatb: ToDatomicCast[B],
             outConv: DatomicDataToArgs[R]
  ): List[R] = QueryExecutor.directQueryOut[R](query, Seq(tdata.to(a).toNative, tdatb.to(b).toNative))(outConv)

  def q[A, B, C, R](query: TypedQueryAuto3[A, B, C, R], a: A, b: B, c: C)(
    implicit tdata: ToDatomicCast[A], tdatb: ToDatomicCast[B], tdatc: ToDatomicCast[C],
             outConv: DatomicDataToArgs[R]
  ): List[R] = QueryExecutor.directQueryOut[R](query, Seq(tdata.to(a).toNative, tdatb.to(b).toNative, tdatc.to(c).toNative))(outConv)

  def q[A, B, C, D, R](query: TypedQueryAuto4[A, B, C, D, R], a: A, b: B, c: C, d:D)(
    implicit tdata: ToDatomicCast[A], tdatb: ToDatomicCast[B], tdatc: ToDatomicCast[C], tdatd: ToDatomicCast[D],
             outConv: DatomicDataToArgs[R]
  ): List[R] = QueryExecutor.directQueryOut[R](query, Seq(tdata.to(a).toNative, tdatb.to(b).toNative, tdatc.to(c).toNative, tdatd.to(d).toNative))(outConv)

  def q[A, B, C, D, E, R](query: TypedQueryAuto5[A, B, C, D, E, R], a: A, b: B, c: C, d:D, e:E)(
    implicit tdata: ToDatomicCast[A], tdatb: ToDatomicCast[B], tdatc: ToDatomicCast[C], tdatd: ToDatomicCast[D], tdate: ToDatomicCast[E],
             outConv: DatomicDataToArgs[R]
  ): List[R] = QueryExecutor.directQueryOut[R](query, Seq(tdata.to(a).toNative, tdatb.to(b).toNative, tdatc.to(c).toNative, tdatd.to(d).toNative, tdate.to(e).toNative))(outConv)

}

/**
 * Converts a Seq[DatomicData] into an Args with potential error (exception)
 */
trait DatomicDataToArgs[T] {
  def toArgs(l: Seq[DatomicData]): T
}

object DatomicDataToArgs extends DatomicDataToArgsImplicitsHidden
