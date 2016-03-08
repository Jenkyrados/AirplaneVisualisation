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
	static final int frameTime = 60; // A frame = 1 min
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

	public static Tuple2<Double,Double> getLatLon(String raw, Double timestamp){
		AirbornePositionMsg localAirpos;
		ModeSReply message;
		String icao;
		PositionDecoder localDec;
		try {
			message = Decoder.genericDecoder(raw);
		} catch (Exception e) {
			System.out.println("Error1");
			return null;
		}
		if (tools.isZero(message.getParity()) || message.checkParity()) { // CRC is ok
			icao = tools.toHexString(message.getIcao24());
		}
		else{
			System.out.println("Error2");
			return null;
		}
		if (icao == null){
			System.out.println("Error3");
			return null;
		}
		if (!(message instanceof AirbornePositionMsg)){
			return null;
		}		
		localAirpos = (AirbornePositionMsg) message;
		
		// decode the position if possible
		if (decs.containsKey(icao)) {
			localDec = decs.get(icao);
			localAirpos.setNICSupplementA(localDec.getNICSupplementA());
			Position current = localDec.decodePosition(timestamp, localAirpos);
			if (current == null){
				System.out.println("Error5 :"+timestamp);
				return null;
			}
			if (current.getLatitude() == null)
				System.out.println("Error6");
			return new Tuple2<Double,Double>(current.getLatitude(),current.getLongitude());
		}
		else {
			System.out.println("Error7");

			localDec = new PositionDecoder();
			localDec.decodePosition(timestamp, localAirpos);
			decs.put(icao, localDec);
			return null;
		}
	}

	public static Iterator<Tuple5<Integer,Double,Double,Double,String>> getNewLatLon(double minTime, Iterable<org.apache.spark.sql.Row> rows){
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

		List<Tuple5<Integer,Double,Double,Double,String>> l = new ArrayList<Tuple5<Integer,Double,Double,Double,String>>();

		PositionDecoder localdec = new PositionDecoder();
		int frameNum = 0;
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
					Position current = localdec.decodePosition(row._1(),airpos);
					if (current != null){
						if(frameNum == 0){
							while(minTime < row._1()){
							frameNum++;
							minTime += frameTime;
							}
						}
						else if (minTime > row._1()){
							continue;
						}
						else {
							minTime += frameTime;
							frameNum++;
						}
						l.add(new Tuple5<Integer,Double,Double,Double,String>(new Integer(frameNum),row._1(),current.getLatitude(),current.getLongitude(),row._3()));
					}
					break;
					default : break;
			}
		}
		return l.iterator();
	}
	/*public static Double getLongitude(String raw, double timestamp){
		try {
			msg = Decoder.genericDecoder(raw);

		} catch (Exception e) {
			return -190.0;
		}
		if (tools.isZero(msg.getParity()) || msg.checkParity()) { // CRC is ok
			icao24 = tools.toHexString(msg.getIcao24());
		}
		else
			return -190.0;
		if (!(msg instanceof AirbornePositionMsg))
			return -190.0;
		airpos = (AirbornePositionMsg) msg;

		// decode the position if possible
		if (decs.containsKey(Long.parseLong(icao24,16))) {
					dec = decs.get(Long.parseLong(icao24,16));
					airpos.setNICSupplementA(dec.getNICSupplementA());
					Position current = dec.decodePosition(timestamp, airpos);
					if (current != null){
						decs.remove(Long.parseLong(icao24,16));
						return current.getLongitude();
					}
					return -190.0;

				}
				else {
					dec = new PositionDecoder();
					dec.decodePosition(timestamp, airpos);
					decs.put(Long.parseLong(icao24,16), dec);
					return -190.0;
				}
	}
	public Tuple3<String, Double, Double> decodeMsg(double timestamp, String raw) throws Exception {
		try {
			msg = Decoder.genericDecoder(raw);
		} catch (BadFormatException e) {
			return null;
		}

		// check for erroneous messages; some receivers set
		// parity field to the result of the CRC polynomial division
		if (tools.isZero(msg.getParity()) || msg.checkParity()) { // CRC is ok
			icao24 = tools.toHexString(msg.getIcao24());
			
			// cleanup decoders every 100.000 messages to avoid excessive memory usage
			// therefore, remove decoders which have not been used for more than one hour.
			List<String> to_remove = new ArrayList<String>();
			for (String key : decs.keySet())
				if (decs.get(key).getLastUsedTime()<timestamp-3600)
					to_remove.add(key);
			
			for (String key : to_remove)
				decs.remove(key);

			// now check the message type

			switch (msg.getType()) {
			case ADSB_AIRBORN_POSITION:
				airpos = (AirbornePositionMsg) msg;

				// decode the position if possible
				if (decs.containsKey(icao24)) {
					dec = decs.get(icao24);
					airpos.setNICSupplementA(dec.getNICSupplementA());
					Position current = dec.decodePosition(timestamp, airpos);
					if (current != null)
						return 
				}
				else {
					dec = new PositionDecoder();
					dec.decodePosition(timestamp, airpos);
					decs.put(icao24, dec);
				}
				break;
			default: break;
			}
		}
		else { // CRC failed
			System.out.println("Message seems to contain biterrors.");
		}
	}*/

}