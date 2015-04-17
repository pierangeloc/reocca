package com.waldoauf.reocca {
/**
 * Cache has targets has configuration settins and entries
 * entries have entry settings and a response
 * Created by Waldo auf der Springe on 4/9/2015.
 */

import org.json4s
import org.json4s.JsonAST._
import spray.http.HttpResponse

import scala.collection.mutable
import scala.collection.mutable.HashMap
import scala.concurrent.Future

class TargetEntry(var key: String = "",
                  var method: String = "get",
                  var reqHeader: String = "",
                  var rspHeader: String = "",
                  var response: JValue = JString("undefined")) {
  override def toString() = s"targetEntry (key: ${key}, method: ${method}},response: ${response})"
}


class EntryListSetting(var entries: List[TargetEntry]) {
  override def toString() = s"cache entries: ${entries}"
}

class CacheTarget(var name: String = "",
                  var replay: Boolean = true,
                  var forward: Boolean = false,
                  var record: Boolean = false,
                  var minSimDelay: BigInt = 0,
                  var maxSimDelay: BigInt = 0,
                  var keyGeneratorName: String = "",
                  var keyGeneratorParameter: String = "",
                  var url: String = "",
                  var filterNamespace: Boolean = false,
                  var skipHttpHeaders: Boolean = false,
                  var decoratorName: String = "",
                  var entries: EntryListSetting = new EntryListSetting(null)) {
  override def toString() = s"CacheTarget name: ${name}, delay: ${minSimDelay}, url: ${url}, entries: ${entries}"
}

object Cache {
  def entrySorter(a: (TargetEntry, CacheTarget), b: (TargetEntry, CacheTarget)) = {
    if (a._2.name == b._2.name) {
      lexicalSorter(a._1.key, b._1.key)
    } else lexicalSorter(a._2.name, b._2.name)

  }

  def updateResponse(eventualResponse: Future[HttpResponse], newResponse: json4s.JValue) = {
    responseMap.remove(eventualResponse) match {
      case Some((targetEntry, _)) => targetEntry.response = newResponse
    }
  }
  def appendResponse(eventualResponse: Future[HttpResponse], pathRemainder: String, newResponse: json4s.JValue) = {
    responseMap.remove(eventualResponse) match {
      case Some((_, cacheTarget)) =>
        cacheTarget.entries.entries = new TargetEntry(response = newResponse) :: cacheTarget.entries.entries
    }
  }
  def lexicalSorter(a: String, b: String): Boolean = {
    if (a.length > b.length) {
      if (a.substring(0, b.length) == b) {
        false
      } else {
        a.compareTo(b) > 0
      }
    } else {
     if (b.substring(0, a.length) == b) {
        true
      } else {
        a.compareTo(b) > 0
      }
    }
  }

  val responseMap = new mutable.HashMap[Future[HttpResponse], (TargetEntry, CacheTarget)]()

  def put(eventualResponse: Future[HttpResponse], pathEntry: (TargetEntry, CacheTarget)) = {
    responseMap.put(eventualResponse, pathEntry)
  }

  type NamedCache = List[CacheTarget]
  val cacheMap = new HashMap[String, NamedCache]()
  def asView(cacheName: String) = {
    cacheMap.get(cacheName) match {
      case Some(namedCache) => {
        for {
          targetEntry <- namedCache
        } yield Map("target" -> targetEntry)
      }
      case _ => null
    }
  }

  def entriesByMethod(method: String): List[(TargetEntry, CacheTarget)] = {
    var result: List[(TargetEntry, CacheTarget)] = Nil
    for {
      cacheName <- cacheMap.keys
    } cacheMap.get(cacheName) match {
      case None => None
      case Some(cache: NamedCache) => {
        for {
          cacheTarget: CacheTarget <- cache
          entry: TargetEntry <- cacheTarget.entries.entries
          if (entry.method == method)
        } result = (entry, cacheTarget) :: result
      }
    }
    result.sortWith(entrySorter)
  }

  def putCache(name: String, jCache: JValue) = {
    val cacheTargetList = for {
      JArray(targetList) <- jCache
      targets <- targetList
      JObject(jTarget) <- targets
      JField("target", JObject(jTargetEntries)) <- jTarget
    } yield {
      var newCacheTarget = new CacheTarget()
      for {
        jTargetEntry <- jTargetEntries
      } {
        jTargetEntry match {
          case JField("name", JString(name)) => newCacheTarget.name = name
          case JField("replay", JBool(name)) => newCacheTarget.replay = name
          case JField("forward", JBool(name)) => newCacheTarget.forward = name
          case JField("record", JBool(name)) => newCacheTarget.record = name
          case JField("minSimDelay", JInt(name)) => newCacheTarget.minSimDelay = name
          case JField("maxSimDelay", JInt(name)) => newCacheTarget.maxSimDelay = name
          case JField("keyGeneratorName", JString(name)) => newCacheTarget.keyGeneratorName = name
          case JField("keyGeneratorParameter", JString(name)) => newCacheTarget.keyGeneratorParameter = name
          case JField("url", JString(name)) => newCacheTarget.url = name
          case JField("filterNamespace", JBool(name)) => newCacheTarget.filterNamespace = name
          case JField("skipHttpHeaders", JBool(name)) => newCacheTarget.skipHttpHeaders = name
          case JField("decoratorName", JString(name)) => newCacheTarget.decoratorName = name
          case JField("entries", JArray(entryFields)) => {
            newCacheTarget.entries = new EntryListSetting(
              for {JObject(entry) <- entryFields
              } yield {
                var targetEntry = new TargetEntry()
                for {entryField <- entry
                }
                entryField match {
                  case JField("key", JString(key)) => targetEntry.key = key
                  case JField("method", JString(key)) => targetEntry.method = key
                  case JField("requestHeader", JString(key)) => targetEntry.reqHeader = key
                  case JField("responseHeader", JString(key)) => targetEntry.rspHeader = key
                  case JField("response", response) => {targetEntry.response = response; println(s"set response of ${targetEntry} to ${response}")}
                  case unmatched => println(s"${unmatched} does not match any entry (key - response")
                }
                targetEntry
              })
          }
          case unmatched => println(s"${unmatched} does not match any json target entry (name - entries: ")
        }
      }
      newCacheTarget
    }
    cacheMap.put(name, cacheTargetList)
  }

}

}