/**
 * copyright 2011-2016
 */
package hybridgraph.examples.mis;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.hama.myhama.api.GraphRecord;
import org.apache.hama.myhama.api.MsgRecord;
import org.apache.hama.myhama.api.UserTool;
import org.apache.hama.myhama.io.EdgeParser;

/**
 * MISUserTool.java
 * Support for {@link MISGraphRecord}, {@link MISMsgRecord}.
 * 
 * @author 
 * @version 0.1
 * 
 * @param <V> vertex value
 * @param <W> edge weight
 * @param <M> message value
 * @param <I> graph information
 */
public class MISUserTool extends UserTool<Integer, Integer, Integer, Integer> {
	private static EdgeParser edgeParser = new EdgeParser();
	
	/**
	 * Vertex value indicates whether or not this vertex belongs to 
	 * the maximal independent set (MIS) S. 
	 * Specifically, 0->unknown, 1->InS, 2->NotInS
	 */
	public static class MISGraphRecord 
			extends GraphRecord<Integer, Integer, Integer, Integer> {
		@Override
	    public void parseGraphData(String vData, String eData) {
			this.verId = Integer.valueOf(vData);
			this.verValue = 0; //initialized as "unknown"
			
			if (eData.equals("")) {
	 			setEdges(new Integer[]{this.verId}, null);
	        	return;
			}
	        
	        setEdges(edgeParser.parseEdgeIdArray(eData, ':'), null);
	    }

		@Override
		public void serVerValue(ByteBuffer vOut) 
				throws EOFException, IOException {
			vOut.putInt(this.verValue);
		}

		@Override
		public void deserVerValue(ByteBuffer vIn) 
				throws EOFException, IOException {
			this.verValue = vIn.getInt();
		}
		
	    @Override
	    public void parseVerValue(String valData) {
	    	this.verValue = Integer.parseInt(valData);
	    }
	    
		@Override
		public Integer[] getWeightArray(int capacity) {
			return null;
		}

		@Override
		public void serEdges(ByteBuffer eOut) 
				throws EOFException, IOException {
			eOut.putInt(this.edgeNum);
	    	for (int index = 0; index < this.edgeNum; index++) {
	    		eOut.putInt(this.edgeIds[index]);
	    	}
		}

		@Override
		public void deserEdges(ByteBuffer eIn) 
				throws EOFException, IOException {
			this.edgeNum = eIn.getInt();
	    	this.edgeIds = new Integer[this.edgeNum];
	    	for (int index = 0; index < this.edgeNum; index++) {
	    		this.edgeIds[index] = eIn.getInt();
	    	}
		}
		
		@Override
		public int getVerByte() {
			return 4;
		}
		
		@Override
		public int getGraphInfoByte() {
			return 0;
		}
		
		/**
		 * 4 + 4 * this.edgeNum
		 */
		@Override
		public int getEdgeByte() {
			return (4 + 4*this.edgeNum);
		}
	}
	
	public static class MISMsgRecord extends MsgRecord<Integer> {	
		@Override
		public void combiner(MsgRecord<Integer> msg) {
			this.msgValue = Math.min(this.msgValue, msg.getMsgValue());
		}
		
		@Override
		public int getMsgByte() {
			return 8; //target-id(4) + msg-value(4)
		}
		
		@Override
	    public void serialize(ByteBuffer out) throws IOException {
	    	out.putInt(this.dstId);
	    	out.putInt(this.msgValue);
	    }
	    
		@Override
	    public void deserialize(ByteBuffer in) throws IOException {
	    	this.dstId = in.getInt();
	    	this.msgValue = in.getInt();
	    }
		
		@Override
		public void deserialize(DataInputStream in) throws IOException {
			this.dstId = in.readInt();
			this.msgValue = in.readInt();
		}
		
		@Override
		public void serialize(DataOutputStream out) throws IOException {
			out.writeInt(this.dstId);
			out.writeInt(this.msgValue);
		}
	}
	
	@Override
	public GraphRecord<Integer, Integer, Integer, Integer> 
			getGraphRecord() {
		return new MISGraphRecord();
	}

	@Override
	public MsgRecord<Integer> getMsgRecord() {
		return new MISMsgRecord();
	}
	
	@Override
	public boolean isAccumulated() {
		return true;
	}
}
