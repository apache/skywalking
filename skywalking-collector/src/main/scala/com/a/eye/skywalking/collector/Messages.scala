package com.a.eye.skywalking.collector

import java.util.ArrayList
import java.util.HashMap

class Word(val word: String, val count: Integer)
case class ResultData()
class MapData(val dataList: ArrayList[Word])
class ReduceData(val reduceDataMap: HashMap[String, Integer])