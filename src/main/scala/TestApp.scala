/* TestApp.scala */
import collection.JavaConverters._
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark.sql.functions.udf
import com.databricks.spark.avro._
import com.databricks.spark.csv._
import org.apache.spark.sql._
import org.apache.commons.io.FileUtils._
import java.io.File
import treater._


object TestApp {
  def main(args: Array[String]) {
    val conf = new SparkConf().setAppName("Simple Application")
    val sc = new SparkContext(conf)
    sc.setLogLevel("WARN")
    val sqlContext = new org.apache.spark.sql.SQLContext(sc)
    deleteQuietly(new File("/Users/quentindauchy/Desktop/save"))

    import sqlContext.implicits._
    val localGetIcao = (arg: String) => {CustomDecoder.getIcao(arg)}
    val sqlGetIcao = udf(localGetIcao)
    val localIsAirborneMsg = (arg: String) => {CustomDecoder.isAirborneMsg(arg)}
    val sqlIsAirborneMsg = udf(localIsAirborneMsg)
    val localGetLatLon = (arg: String,time:Double) => {CustomDecoder.getLatLon(arg,time)}
    val sqlGetLatLon = udf(localGetLatLon)

    val df = sqlContext.read.avro("/Users/quentindauchy/truc/BigData/assign2/osky-sample/raw20150421_sample.avro")
    val rddicao = df
        .select("timeAtServer","rawMessage")
        .withColumn("icao",sqlGetIcao(df("rawMessage")))
        .filter("icao != 'thisisanerror'")
        .rdd
    val minTime = rddicao.min()(new Ordering[org.apache.spark.sql.Row]() {
      override def compare(x: org.apache.spark.sql.Row, y: org.apache.spark.sql.Row): Int = 
          Ordering[Double].compare(x(0).asInstanceOf[Double], y(0).asInstanceOf[Double])
    });
    rddicao
        .groupBy(x => x(2))
        .flatMap(x => CustomDecoder.getNewLatLon(minTime(0).asInstanceOf[Double],x._2.asJava).asScala.toList)
        .map(a => a._1+","+a._2+","+a._3+","+a._4+","+a._5).saveAsTextFile("/Users/quentindauchy/Desktop/save")
  }

}
