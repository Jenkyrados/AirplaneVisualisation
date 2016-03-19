name := "Test Project"

version := "1.0"

scalaVersion := "2.10.5"
unmanagedJars in Compile += file("lib/libadsb-1.1-fat.jar")

libraryDependencies += "com.databricks" %% "spark-avro" % "2.0.1"
libraryDependencies += "com.databricks" %% "spark-csv" % "1.4.0"
