package treater;
/**
 *  This file is part of org.opensky.libadsb.
 *
 *  org.opensky.libadsb is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  org.opensky.libadsb is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with org.opensky.libadsb.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Iterator;
import java.util.Comparator;
import java.util.Collections;

import org.opensky.libadsb.Decoder;
import org.opensky.libadsb.Position;
import org.opensky.libadsb.PositionDecoder;
import org.opensky.libadsb.tools;
import org.opensky.libadsb.exceptions.BadFormatException;
import org.opensky.libadsb.msgs.AirbornePositionMsg;
import org.opensky.libadsb.msgs.ModeSReply;
import scala.Tuple2;
import scala.Tuple3;
import scala.Tuple4;
import scala.Tuple5;
import scala.collection.JavaConverters.*;

public class CustomDecoder {
	// tmp variables for the different message types
	private static AirbornePositionMsg airpos;
	private static ModeSReply msg;
	private static String icao24;

	// we store the position decoder for each aircraft
	static HashMap<String, PositionDecoder> decs = new HashMap<String, PositionDecoder>();
	private static PositionDecoder dec;
	static final double frameTime = 1800; // A frame = 30 min
	
	public static String getIcao(String raw){
		ModeSReply message;
		try {
			message = Decoder.genericDecoder(raw);
		} catch (Exception e) {
			return "thisisanerror";
		}
		if (tools.isZero(message.getParity()) || message.checkParity()) { // CRC is ok
			return tools.toHexString(message.getIcao24());
		}
		return "thisisanerror";
	}

	public static boolean isAirborneMsg(String raw){
		try {
			msg = Decoder.genericDecoder(raw);
			airpos = (AirbornePositionMsg) msg;
		} catch (Exception e) {
			return false;
		}
		switch(msg.getType()){
			case ADSB_AIRBORN_POSITION : return true;
			default: return false;
		}
	}

	public static Iterator<Tuple4<String,Integer,Integer,String>> getNewLatLon(double minTime, Iterable<org.apache.spark.sql.Row> rows){
		Comparator<Tuple3<Double,String,String>> compareRows = new Comparator<Tuple3<Double,String,String>>(){
			public int compare(Tuple3<Double,String,String> rowA,
				Tuple3<Double,String,String> rowB){
				return rowA._1().compareTo(rowB._1());
			}
		};
		List<Tuple3<Double,String,String>> rowList = new ArrayList<Tuple3<Double,String,String>>();
		for(org.apache.spark.sql.Row row : rows)
			rowList.add(new Tuple3<Double,String,String>(new Double(row.getDouble(0)),row.getString(1),row.getString(2)));
		Collections.sort(rowList,compareRows);

		List<Tuple4<String,Integer,Integer,String>> k = new ArrayList<Tuple4<String,Integer,Integer,String>>();

		PositionDecoder localdec = new PositionDecoder();
		int frameNum = 0;
		int firstValidFrame = 0;
		int frameWidth = 0;
		String positions = "";
		String icao24 = "";
		for(Tuple3<Double,String,String> row : rowList){
			ModeSReply message;
			try {
				message = Decoder.genericDecoder(row._2());
			
			}
			catch(Exception e){
				continue;
			}
			switch(message.getType()){
				case ADSB_AIRBORN_POSITION:
					AirbornePositionMsg airpos = (AirbornePositionMsg) message;
					airpos.setNICSupplementA(localdec.getNICSupplementA());
					icao24 = row._3();
					Position current = localdec.decodePosition(row._1(),airpos);
					if (current != null){
						if(frameNum == 0){
							while(minTime < row._1()){
								frameNum++;
								minTime += frameTime;
							}
							firstValidFrame = frameNum;
						}
						else if (minTime > row._1()){
							continue;
						} 
						else {
							while(minTime < row._1()){
								frameNum++;
								minTime += frameTime;
								frameWidth++;
							}
						}
						if (frameWidth > 1) {
							k.add(new Tuple4<String,Integer,Integer,String>(icao24, new Integer(firstValidFrame), new Integer(frameNum-frameWidth), positions));
							positions = "";
							firstValidFrame = frameNum;
						}
						positions += Double.toString(current.getLongitude()) + ";" + Double.toString(current.getLatitude()) + " ";
						frameWidth = 0;
					}
					break;
				default : break;
			}
		}

		if( icao24 != "" && firstValidFrame > 0 )
			k.add(new Tuple4<String,Integer,Integer,String>(icao24, new Integer(firstValidFrame), new Integer(frameNum), positions));
		
		return k.iterator();
	}
	

}