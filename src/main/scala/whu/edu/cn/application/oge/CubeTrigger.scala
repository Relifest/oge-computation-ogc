package whu.edu.cn.application.oge

import com.alibaba.fastjson.JSON
import org.apache.spark.{SparkConf, SparkContext}
import whu.edu.cn.jsonparser.{JsonToArg, JsonToArgLocal}

import scala.collection.mutable.Map
import scala.io.Source

object CubeTrigger {
  //  var rdd_list:Map[String, RDD[(SpaceTimeBandKey, (Tile,Array[Double]))]] = Map.empty[String, RDD[(SpaceTimeBandKey, (Tile, Array[Double]))]]
  var rdd_list_cube: Map[String, Map[String, Any]] = Map.empty[String, Map[String, Any]]
  var cubeLoad: Map[String, (String, String, String)] = Map.empty[String, (String, String, String)]

  def argOrNot(args: Map[String, String], name: String): String = {
    if (args.contains(name)) {
      args(name)
    }
    else {
      null
    }
  }

  def func(implicit sc: SparkContext, UUID: String, name: String, args: Map[String, String]): Unit = {
    if (name == "Service.getCollections") {
      cubeLoad += (UUID -> (argOrNot(args, "productIDs"), argOrNot(args, "datetime"), argOrNot(args, "bbox")))
    }
    if (name == "Collections.toCube") {
      rdd_list_cube += (UUID -> Cube.load(sc, productList = cubeLoad(args("input"))._1, dateTime = cubeLoad(args("input"))._2, geom = cubeLoad(args("input"))._3, bandList = argOrNot(args,"bands")))
    }
    if (name == "Cube.NDWI") {
      rdd_list_cube += (UUID -> Cube.NDWI(input = rdd_list_cube(args("input")), product = argOrNot(args, "product"), name = argOrNot(args, "name")))
    }
    if(name == "Cube.binarization") {
      rdd_list_cube += (UUID -> Cube.binarization(input = rdd_list_cube(args("input")), product = argOrNot(args, "product"), name = argOrNot(args, "name"),
        threshold = argOrNot(args, "threshold").toDouble))
    }
    if (name == "Cube.subtract") {
      rdd_list_cube += (UUID -> Cube.WaterChangeDetection(input = rdd_list_cube(args("input")), product = argOrNot(args, "product"),
        certainTimes = argOrNot(args, "timeList"), name = argOrNot(args, "name")))
    }
    if (name == "Cube.overlayAnalysis") {
      rdd_list_cube += (UUID -> Cube.OverlayAnalysis(input = rdd_list_cube(args("input")), rasterOrTabular = argOrNot(args, "raster"), vector = argOrNot(args, "vector"),name = argOrNot(args, "name")))
    }
    if (name == "Cube.addStyles") {
      Cube.visualize(sc, cube = rdd_list_cube(args("cube")), products = argOrNot(args, "products"))
    }
  }

  def lamda(implicit sc: SparkContext, list: List[Tuple3[String, String, Map[String, String]]]) = {
    for (i <- list.indices) {
      func(sc, list(i)._1, list(i)._2, list(i)._3)
    }
  }

  def main(args: Array[String]): Unit = {
    val time1 = System.currentTimeMillis()
    val conf = new SparkConf()
      .setAppName("GeoCube-Dianmu Hurrican Flood Analysis")
      .setMaster("local[*]")
      .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .set("spark.kryo.registrator", "geotrellis.spark.store.kryo.KryoRegistrator")
      .set("spark.kryoserializer.buffer.max", "512m")
      .set("spark.rpc.message.maxSize", "1024")
    val sc = new SparkContext(conf)

    val line: String = Source.fromFile("src/main/scala/whu/edu/cn/application/oge/testJsonCubeNDWITimeSeries.json").mkString
//    val line: String = Source.fromFile("src/main/scala/whu/edu/cn/application/oge/testJsonCubeFloodAnalysis.json").mkString
    val jsonObject = JSON.parseObject(line)
    println(jsonObject.size())
    println(jsonObject)

    val a = JsonToArgLocal.trans(jsonObject)

    println(a.size)
    a.foreach(println(_))

    lamda(sc, a)

    val time2 = System.currentTimeMillis()
    println(time2 - time1)
  }
}