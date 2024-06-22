import org.apache.spark.sql._
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._

object SparkTask1 {
    def getSparkSession: SparkSession = {
        val spark = SparkSession
                .builder()
                .master("local[*]")
                .appName("Spark Mandatory task 1")
                .config("spark.driver.bindAddress", "127.0.0.1")
                .config("spark.driver.port", "7077")
                .config("spark.hadoop.fs.s3a.aws.credentials.provider", "org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider")
                .config("spark.cassandra.connection.host","cassandra.ap-south-1.amazonaws.com")
                .config("spark.cassandra.connection.port", "9142")
                .config("spark.cassandra.connection.ssl.enabled", "true")
                .config("spark.cassandra.auth.username", sys.env.getOrElse("CASSANDRA_USER", ""))
                .config("spark.cassandra.auth.password", sys.env.getOrElse("CASSANDRA_PASS", ""))
                .config("spark.cassandra.input.consistency.level", "LOCAL_QUORUM")
                .config("spark.cassandra.connection.ssl.trustStore.path", "/Users/srinadh/cassandra_truststore.jks")
                .config("spark.cassandra.connection.ssl.trustStore.password", sys.env.getOrElse("TRUSTSTORE_PASS", ""))
                .getOrCreate()

        val hadoopConfig = spark.sparkContext.hadoopConfiguration
        hadoopConfig.set("fs.s3a.access.key", sys.env.getOrElse("AWS_ACCESS_KEY", ""))
        hadoopConfig.set("fs.s3a.secret.key", sys.env.getOrElse("AWS_SECRET_KEY", ""))
        hadoopConfig.set("fs.s3a.endpoint", "s3.amazonaws.com")
        hadoopConfig.set("fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
        hadoopConfig.set("fs.s3a.path.style.access", "true")  
        hadoopConfig.set("fs.s3a.connection.ssl.enabled", "false")
        hadoopConfig.set("fs.s3a.region", "ap-south-1")
        spark
    }

    def readCsv(path: String, spark: SparkSession): DataFrame = {
        val df = spark.read
                .option("header", "true")
                .option("inferSchema", "true")
                .csv(path)
        df
    }

    def writeToCassandraKeySpace(df: DataFrame, spark: SparkSession, tableName: String, keySpaceName: String): Unit = {
        df.write
            .format("org.apache.spark.sql.cassandra")
            .options(Map("table" -> tableName, "keyspace" -> keySpaceName))
            .mode("append")
            .save()
    }

    def readFromCassandraKeySpace(spark: SparkSession, tableName: String, keySpaceName: String): DataFrame = {
        val df = spark.read
                    .format("org.apache.spark.sql.cassandra")
                    .options(Map("table" -> tableName, "keyspace" -> keySpaceName))
                    .load()
        df
    }

    def writeToS3Parquet(df: DataFrame, outputPath: String): Unit = {
        df.write.mode("overwrite").parquet(outputPath)
    }

    def readFromS3Parquet(spark: SparkSession, path: String): DataFrame = {
        val df = spark.read.parquet(path)
        df
    }
 
    def renameColumns(df: DataFrame): DataFrame = {
        val renamedCols = df.columns.foldLeft(df) { (tempDF, colName) =>
        tempDF.withColumnRenamed(colName, colName.replace(" ", "_").replace("(", "").replace(")", "").replace("-", "_"))
        }
        renamedCols
    }

    def printDataFrame(df: DataFrame): Unit = {
        df.show
    }

    def main(args: Array[String]): Unit = {
        val spark = getSparkSession

        val input_path = "s3a://spark-final-assignment/input-data/zaragoza_data.csv"
        val output_path = "s3a://spark-final-assignment/output-data/zaragoza_data/"
                
        //reading file from S3
        val zaragozaDataDf = readCsv(input_path, spark)

        //writing data to cassandra keyspace
        // writeToCassandraKeySpace(zaragozaDataDf, spark, "zaragoza_data", "sparkAssignment")

        //read from cassandra keyspace
        // val zaragozaDataCassandraDf = readFromCassandraKeySpace(spark, "zaragoza_data", "sparkAssignment")

        //writing data to S3
        // val zaragozaDataRenamedDf = renameColumns(zaragozaDataCassandraDf) 
        // writeToS3Parquet(zaragozaDataRenamedDf, output_path)

        //reading from S3 parquet
        val zaragozaDataParquetDf = readFromS3Parquet(spark, output_path)
        printDataFrame(zaragozaDataParquetDf)

        // 1. Calculate the average NO2 levels per station
        val avgNO2PerStation = zaragozaDataParquetDf.groupBy("station_name").agg(avg("NO2").alias("avg_NO2"))
        printDataFrame(avgNO2PerStation)
        
        // 2. Calculate the maximum O3 levels per station
        val maxO3PerStation = zaragozaDataParquetDf.groupBy("station_name").agg(max("O3").alias("max_O3"))
        printDataFrame(maxO3PerStation)

        // 3. Calculate the total PM10 levels per station
        val totalPM10PerStation = zaragozaDataParquetDf.groupBy("station_name").agg(sum("PM10").alias("total_PM10"))
        printDataFrame(totalPM10PerStation)
    
        // 4. Calculate the average temperature per station
        val avgTempPerStation = zaragozaDataParquetDf.groupBy("station_name").agg(avg("Temp").alias("avg_Temp"))
        printDataFrame(avgTempPerStation)
    } 
}