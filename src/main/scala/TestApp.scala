/* TestApp.scala */
import collection.JavaConverters._
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark.sql.functions.udf
import com.databricks.spark.avro._
import com.databricks.spark.csv._
import org.apache.spark.sql._
import treater._


  object TestApp {
    def main(args: Array[String]) {
      val conf = new SparkConf().setAppName("Simple Application")
      val sc = new SparkContext(conf)
      sc.setLogLevel("WARN")
      val sqlContext = new org.apache.spark.sql.SQLContext(sc)

      import sqlContext.implicits._
      val localGetIcao = (arg: String) => {CustomDecoder.getIcao(arg)}
      val sqlGetIcao = udf(localGetIcao)
      val localIsAirborneMsg = (arg: String) => {CustomDecoder.isAirborneMsg(arg)}
      val sqlIsAirborneMsg = udf(localIsAirborneMsg)
      val localGetLatLon = (arg: String,time:Double) => {CustomDecoder.getLatLon(arg,time)}
      val sqlGetLatLon = udf(localGetLatLon)

      val df = sqlContext.read.avro("/Users/quentindauchy/truc/BigData/assign2/osky-sample/raw20150421_sample.avro")
      val dficao = df
          .select("timeAtServer","rawMessage")
          .withColumn("icao",sqlGetIcao(df("rawMessage")))
          .filter("icao != 'thisisanerror'")
          .rdd
          .groupBy(x => x(2))
          .flatMap(x => CustomDecoder.getNewLatLon((x._1.toString(),x._2.asJava)).asScala.toList)
          .map(a => a._1+","+a._2+","+a._3).saveAsTextFile("/Users/quentindauchy/Desktop/save")
/*      val dfLonLat = dficao.
         .withColumn("latlon",sqlGetLatLon(dficao("rawMessage"),dficao("timeAtServer")))
      dfLonLat.write.format("com.databricks.spark.csv").save("/Users/quentindauchy/Desktop/home.csv")
      dfLonLat.show
      dfLonLat.printSchema
  */  }

  }