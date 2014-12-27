import org.apache.spark.streaming.{Seconds, StreamingContext}
import StreamingContext._
import org.apache.spark.SparkContext._
import org.apache.spark.streaming.twitter._
import scala.math.Ordering.Implicits._
import scala.collection.mutable.PriorityQueue
import java.util.{Date, Locale}
import java.text.DateFormat
import java.text.DateFormat._
import scala.math


object Timer {
  def apply(interval: Int, repeats: Boolean = true)(op: => Unit) {
    val timeOut = new javax.swing.AbstractAction() {
      def actionPerformed(e : java.awt.event.ActionEvent) = op
    }
    val t = new javax.swing.Timer(interval, timeOut)
    t.setRepeats(repeats)
    t.start()
  }
}

object ReservoirSampling {

  def main(args: Array[String]) {
    if (args.length < 1) {
      System.err.println("Give the first argument as local[n]")
      System.exit(1)
    }


// Twitter Authentication credentials
    System.setProperty("twitter4j.oauth.consumerKey", "VqhVnvQqo3KClMfizodvKHy7p")
    System.setProperty("twitter4j.oauth.consumerSecret", "eOZMVy0gZVPmLEejtHtwvsOGtIivF9PGaqK5n1bpzavEdlMBjO")
    System.setProperty("twitter4j.oauth.accessToken", "454675262-qdAW5n6xLDeTsxdMWzyUfEg62ct5IBHdLIHW352T")
    System.setProperty("twitter4j.oauth.accessTokenSecret", "QsSo9WT5swXnvEXuKpJc7FPIkhPIGpQevOcR0a1LM7VLh")

// create the spark streaming context 
   
    val ssc = new StreamingContext(args(0), "ReservoirSampling", Seconds(5),System.getenv("SPARK_HOME"), StreamingContext.jarOfClass(this.getClass))

	val tweetstream = TwitterUtils.createStream(ssc, None)
//get status with the tweetId and text
	val statuses = tweetstream.map((status) => (status.getId(),status.getText()))


	val words = statuses.flatMapValues(v=> v.split(" "))
	val ht = words.filter{case(x,y)=> y.startsWith("#")}

//p--sampling probability = 0.2 (assumed)
//
/*
failure Rate, delta = 0.0000005 

-2log(delta)/3
= 307
-log(delta) = 460
*/


	val temp = ht.map({case(key,value)=> {
 	val r = scala.util.Random; 
	val pa= r.nextFloat;

	if (pa< math.max(0,(0.2+ 307/key - math.sqrt((math.pow(307/key,2))+3*0.2*307/key) ) ) ) 
		(pa-1,value)
	else if (pa>math.min(1, ( 0.2+ 460/key+ math.sqrt(math.pow(460/key,2)+2*0.2*460/key ) )  ))
		(pa, value)
		 
	else
		(pa+1, value)
	}})

	val eliminate = temp.filter{case(x,y)=> x>1}
	val temp1 = temp.transform(_.sortByKey(true))
	val last20 = temp1.window(Seconds(20))
	
//save the RDDs in folders.. prefix startes with answer... suffix is empty string
	last20.saveAsTextFiles("answers","")
//considering equal contribution from the RDDs.. 
	
	last20.foreachRDD(rdd=>{
	val top10= rdd.take(10)
	top10.foreach{case (key) => println("%s \n".format(key))}
	println("checkingggggggThis is being printed! ")
	//rdd.foreach{case (key, value) => println(" with probab %s tweeted %s\n".format(key, value))}


	})


	ssc.start()
	Timer(60000) { 
		println("Timer went off") 
		ssc.stop()
		}

//	ssc.awaitTermination()
  }
}
