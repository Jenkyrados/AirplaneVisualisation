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
    val conf = new SparkConf().setAppName("Aero Spark")
    val sc = new SparkContext(conf)
    sc.setLogLevel("WARN")
    val sqlContext = new org.apache.spark.sql.SQLContext(sc)
    deleteQuietly(new File("hdfs://hathi-surfsara/user/lsde07/final_out"))

    import sqlContext.implicits._
    val localGetIcao = (arg: String) => {CustomDecoder.getIcao(arg)}
    val sqlGetIcao = udf(localGetIcao)

    val df = sqlContext.read.avro("hdfs://hathi-surfsara/user/hannesm/lsde/opensky/*.avro")
    val rddicao = df
        .select("timeAtServer","rawMessage")
        .withColumn("icao",sqlGetIcao(df("rawMessage")))
        .filter("icao != 'thisisanerror'")
        .rdd
    val minTime = rddicao.min()(new Ordering[org.apache.spark.sql.Row]() {
      override def compare(x: org.apache.spark.sql.Row, y: org.apache.spark.sql.Row): Int = 
          Ordering[Double].compare(x(0).asInstanceOf[Double], y(0).asInstanceOf[Double])
    });

    val r = scala.util.Random;

    rddicao
        .groupBy(x => x(2))
        .sample(false, 0.1, r.nextInt(100))
        .flatMap(x => CustomDecoder.getNewLatLon(minTime(0).asInstanceOf[Double],x._2.asJava).asScala.toList)
        .map(a => a._1+","+a._2+","+a._3+","+a._4).coalesce(1, true).saveAsTextFile("hdfs://hathi-surfsara/user/lsde07/final_out")
  }

}
