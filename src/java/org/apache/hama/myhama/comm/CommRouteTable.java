package org.apache.hama.myhama.comm;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.ipc.RPC;
import org.apache.hama.bsp.BSPJob;
import org.apache.hama.ipc.CommunicationServerProtocol;
import org.apache.hama.monitor.GlobalSketchGraph;
import org.apache.hama.monitor.GlobalStatistics;

public class CommRouteTable {
	private static final Log LOG = LogFactory.getLog(CommRouteTable.class);
	private GlobalStatistics global;
	private GlobalSketchGraph skGraph;
	private BSPJob job;
	private int parId;
	private int taskNum;
	private int bucNum;
	private InetSocketAddress[] inetAddresses;
	private int[] parIds, mins, maxs, lens;
	private int maxLen;
	private Map<InetSocketAddress, CommunicationServerProtocol> comms = 
			new HashMap<InetSocketAddress, CommunicationServerProtocol>();
	
	public CommRouteTable(BSPJob _job, int parId) {
		this.parId = parId;
		job = _job;
		taskNum = job.getNumBspTask();
		inetAddresses = new InetSocketAddress[taskNum];
		parIds = new int[taskNum]; lens = new int[taskNum];
		mins = new int[taskNum+1]; maxs = new int[taskNum];
	}
	
	public void initialilze(GlobalStatistics _global) {
		this.global = _global;
		this.skGraph = _global.getGlobalSketchGraph();
		this.bucNum = this.skGraph.getBucNumTask(this.parId);
		int[] tmpParIds = global.getTaskIds();
		int[] tmpMins = global.getRangeMinIds();
		int[] tmpMaxs = global.getRangeMaxIds();
		int[] tmpPorts = global.getPorts();
		String[] tmpHostNames = global.getHostNames();
		
		for (int i = 0; i < taskNum; i++) {
			this.inetAddresses[i] = new InetSocketAddress(tmpHostNames[i], tmpPorts[i]);
		}
		
		for (int i = 0; i < taskNum; i++) {
			parIds[i] = tmpParIds[i];
			mins[i] = tmpMins[i];
			maxs[i] = tmpMaxs[i];
		}
		
		resortRouteTable();
		maxLen = findMaxLength();
	}
	
	public void resetGlobalStatis(GlobalStatistics _global) {
		global = _global;
	}
	
	public int getTaskNum() {
		return this.taskNum;
	}
	
	public int getLocBucNum() {
		return this.bucNum;
	}
	
	public GlobalStatistics getGlobalStatis() {
		return this.global;
	}
	
	public GlobalSketchGraph getGlobalSketchGraph() {
		return this.skGraph;
	}
	
	/**
	 * Get the destination partitionId according to the vertexId.
	 * If the destination partitionId is not found, the last partition is the default one.
	 * @param vertexId
	 * @return
	 */
	public int getDstParId(int vId) {
		int counter = (vId - mins[0]) / maxLen;
		try {
			if(counter >= taskNum) {
				return parIds[taskNum-1];
			}
			
			mins[taskNum] = vId;
			for (; vId > mins[counter]; counter++);
			if (vId == mins[counter] && counter < taskNum) {
				return parIds[counter];
			} else {
				return parIds[counter-1];
			}
		} catch (Exception e) {
			LOG.error("getDstParId: vId=" + vId + " counter=" + counter, e);
			return -1;
		}
		
		/*int counter = 0;
		int length = this.rangeMins.length - 1;
		for (counter = 1; counter < length; counter++) {
			if (vertexId < this.rangeMins[counter]) {
				break;
			}
		}
		return this.rangeParIds[counter - 1];*/
	}
	
	/**
	 * Get the destination bucket Id in partition _dstParId, according to a given _vId.
	 * @param vertexId
	 * @return
	 */
	public int getDstBucId(int _dstParId, int _vId) {
		return this.skGraph.getTaskBucIndex(_dstParId, _vId);
	}
	
	/**
	 * Get the inet communication address according to the dstPartitionId.
	 * 
	 * @param dstPartitionId
	 * @return
	 */
	public InetSocketAddress getInetSocketAddress(int dstParId) {
		return inetAddresses[dstParId];
	}
	
	/**
	 * Get the CommunicationServer Object according to the InetSocketAddress.
	 * If the CommunicationServer Object does not exist, then create and save
	 * it in the CommunicationServer Cache.
	 * @param addr
	 * @return
	 */
	public synchronized CommunicationServerProtocol getCommServer(InetSocketAddress addr) {
		if (!comms.containsKey(addr) || comms.get(addr)==null) {
			generateCommServer(addr);
		}
		
		return comms.get(addr);
	}
	
	private void generateCommServer(InetSocketAddress addr) {
		try {
			CommunicationServerProtocol comm = (CommunicationServerProtocol) RPC.getProxy(
					CommunicationServerProtocol.class, CommunicationServerProtocol.versionID,
					addr, job.getConf());
			comms.put(addr, comm);
		} catch (Exception e) {
			LOG.error("[generateCommServer]", e);
		}
	}
	
	/**
	 * Find the max length among all partitions.
	 * 
	 * @return maxLength int
	 */
	private int findMaxLength() {
		int result = 0;
		for (int i = 0; i < taskNum; i++) {
			if (result < lens[i]) {
				result = lens[i];
			}
		}
		
		return result;
	}
	
	/**
	 * Resort the route table of range-partition according to the minimum of range.
	 * Now we just adopt the simple algorithm "BubbleSort".
	 */
	private void resortRouteTable() {
		int i, j, swap;
		for (i = 0; i < taskNum; i++) {
			for (j = i + 1; j < taskNum; j++) {
				if (mins[i] > mins[j]) {
					swap = mins[j];	mins[j] = mins[i]; mins[i] = swap;
					swap = maxs[j]; maxs[j] = maxs[i]; maxs[i] = swap;
					swap = parIds[j]; parIds[j] = parIds[i]; parIds[i] = swap;
				}
			}
		}
		
		for(i = 0; i < taskNum; i++) {
			lens[i] = maxs[i] - mins[i] + 1;
		}
		
		showRouteTable();
	}
	
	private void showRouteTable() {
		StringBuffer sb = new StringBuffer("Global Route Table Information:");
		
		for (int index = 0; index < taskNum; index++) {
			sb.append("\n");
			sb.append("[ParId] "); sb.append(parIds[index]); sb.append("\t");
			sb.append("[MinId] "); sb.append(mins[index]);
			sb.append("[Len] "); sb.append(lens[index]);
		}
		LOG.info(sb.toString());
	}
}