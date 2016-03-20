name := "Test Project"

version := "1.0"

scalaVersion := "2.10.5"
javacOptions ++= Seq("-source", "1.7", "-target", "1.7")
scalacOptions += "-target:jvm-1.7"
unmanagedJars in Compile += file("lib/libadsb-1.1-fat.jar")

libraryDependencies += "com.databricks" %% "spark-avro" % "2.0.1"
libraryDependencies += "com.databricks" %% "spark-csv" % "1.4.0"
libraryDependencies += "org.apache.spark" %% "spark-core" % "1.6.0" % "provided"
libraryDependencies += "org.apache.spark" %% "spark-sql" % "1.6.0" % "provided"

//libraryDependencies ~= { _.map(_.exclude("org.slf4j","slf4j-log4j12"))  }
