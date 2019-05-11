package spark;

import Utils.Constants;
import Utils.Context;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.PairFlatMapFunction;
import org.apache.spark.api.java.function.PairFunction;
import scala.Tuple2;
import scala.Tuple3;
import scala.Tuple4;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class Query1 {


    public static void executeQuery(String[] args){

        // SparkContext creation
        JavaSparkContext sc= Context.getContext("Query1");

        /*
        Read csv file From hdfs (or local file system)
        creation of RDD<String> from csv file
        */
        JavaRDD<String> weatherFile = sc.textFile(Constants.WEATHER_FILE);
        String firstLine = weatherFile.first();
        List<String> citiesArray = new ArrayList<>(Arrays.asList(firstLine.split(",")));
        citiesArray.remove(0);

        /*
        .filter : Remove Header

        .flatMapToPair :
            RDD<String> to RDD<K,V> where:
                K = ( year , month , day , city )
                V = 0 or 1
                    1 : description was "sky is clear"
                    0 : other

        .filter :
            RDD<(year,month,day,city), value > on Month: March.April,May

        .reduceByKey :
            Sum values with same day:
            In this way we calculate the number of "sky is clear" in a day

        .mapToPair :
             RDD<(year,month,day,city), count> to RDD<K,V> where:
                K : (year,month,city)
                V : 0 or 1
                    1 : count > N
                        N is the number of "sky is clear" needed to consider "clear" a day
                    0 : else
        .reduceByKey :
            Sum values with same month:
            In this way we calculate the number of clear days in a month

        .filter :
            RDD<(year,month,city), count>  on count: take the Month with at least 15 clear day

        .mapToPair :
            RDD<(year,month,city), count>  to RDD< K,V> where :
                K = year/month
                V = cities

        .groupByKey : group by year/month
        .sortByKey : order by year/month
         */



        JavaPairRDD<Integer, Iterable<String>> citiesWithClearSky = weatherFile
                .filter( csvLine -> !csvLine.equals(firstLine) )
                .flatMapToPair((PairFlatMapFunction<String, Tuple4<Integer, Integer, Integer, String>, Integer>) s -> {
                    String[] strings = s.split(",", -1);
                    DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    Date date = df.parse(strings[0]);
                    GregorianCalendar cal = new GregorianCalendar();
                    cal.setTime(date);
                    List<Tuple2<Tuple4<Integer,Integer,Integer,String>,Integer>> list = new ArrayList<>();
                    for (int i = 1; i < strings.length; i++) {
                        list.add( new Tuple2<>(new Tuple4<>(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),citiesArray.get(i-1) ), strings[i].equals("sky is clear") ? 1 : 0));
                    }
                    return list.iterator();

                })
                .filter( object -> (
                        object._1._2() == 2 ||
                                object._1._2() == 3 ||
                                object._1._2() == 4 ) )
                .reduceByKey(Integer::sum)
                .mapToPair((PairFunction<Tuple2<Tuple4<Integer, Integer, Integer, String>, Integer>, Tuple3<Integer, Integer, String>, Integer>) tuple -> {
                    if (tuple._2 >= 18){ //se almeno 12 ore in un giorno sono serene
                        return new Tuple2<>(new Tuple3<>(tuple._1._1(),tuple._1._2(),tuple._1._4()), 1);
                    }else {
                        return new Tuple2<>(new Tuple3<>(tuple._1._1(),tuple._1._2(),tuple._1._4()), 0);
                    }
                })
                .reduceByKey(Integer::sum)
                .filter((Function<Tuple2<Tuple3<Integer, Integer, String>, Integer>, Boolean>) tuple -> {
                    // città con più di 15 giorni al mese con tempo sereno
                    return tuple._2 >= 15;
                })
                .mapToPair((PairFunction<Tuple2<Tuple3<Integer, Integer, String>, Integer>, Tuple2<Integer,String>,Integer>) tuple -> {
                    return new Tuple2<>(new Tuple2<>(tuple._1()._1(),tuple._1._3()),1);
                })
                .reduceByKey(Integer::sum)
                .filter(v1 -> v1._2()>=3)
                .mapToPair( tuple ->  tuple._1())
                .groupByKey()
                .sortByKey();


        for(Tuple2<Integer, Iterable<String>> x :citiesWithClearSky.collect()) {
            System.out.println(x._1 + "  " + x._2);
        }

        citiesWithClearSky.saveAsTextFile("clearSky");

        sc.stop();

    }


}
