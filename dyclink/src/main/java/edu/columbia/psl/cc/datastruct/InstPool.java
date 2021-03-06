package edu.columbia.psl.cc.datastruct;

import java.util.Comparator;
import java.util.HashMap;
import java.util.TreeSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.columbia.psl.cc.pojo.FieldNode;
import edu.columbia.psl.cc.pojo.InstNode;
import edu.columbia.psl.cc.pojo.MethodNode;
import edu.columbia.psl.cc.pojo.MethodNode.RegularState;
import edu.columbia.psl.cc.util.GlobalGraphRecorder;
import edu.columbia.psl.cc.util.StringUtil;

public class InstPool extends TreeSet<InstNode> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private static Logger logger = LogManager.getLogger(InstPool.class);
	
	public static boolean DEBUG;
	
	public static int REGULAR = 0;
	
	public static int METHOD = 1;
	
	public static int FIELD = 2;
	
	private HashMap<String, InstNode> instMap = new HashMap<String, InstNode>();
	
	public InstPool() {
		super(new InstNodeComp());
	}
	
	private void updateTime(InstNode fullInst) {
		long curTime = GlobalGraphRecorder.getCurTime();
		if (fullInst.getStartTime() < 0) {
			fullInst.setStartTime(curTime);
			fullInst.setUpdateTime(curTime);
		} else {
			fullInst.setUpdateTime(curTime);
		}
 	}
	
	private boolean _addInst(String idxKey, InstNode inst) {
		if (super.add(inst)) {
			this.instMap.put(idxKey, inst);
			return true;
		} else {
			return false;
		}
	}
	
	private boolean _removeInst(String idxKey, InstNode inst) {	
		if (super.remove(inst)) {
			this.instMap.remove(idxKey);
			return true;
		} else {
			return false;
		}
	}
	
	public InstNode searchAndGet(String methodKey, 
			int threadId, 
			int threadMethodIdx, 
			int idx, 
			int opcode, 
			String addInfo, 
			int request) {
		String idxKey = StringUtil.genIdxKey(threadId, threadMethodIdx, idx);
		if (this.instMap.containsKey(idxKey)) {
			InstNode ret = this.instMap.get(idxKey);
			
			//Handle the case that the instruction type changes (rarely happens, for interface method)
			if ((request == METHOD) && !(ret instanceof MethodNode)) {
				//InstNode first and then method
				logger.info("Inst node type changes: " + ret);
				
				InstNode newRet = new MethodNode();
				newRet.setFromMethod(ret.getFromMethod());
				newRet.setThreadId(ret.getThreadId());
				newRet.setThreadMethodIdx(ret.getThreadMethodIdx());
				newRet.setIdx(ret.getIdx());
				newRet.setOp(ret.getOp());
				newRet.setAddInfo(ret.getAddInfo());
				newRet.setStartTime(ret.getStartTime());
				newRet.setUpdateTime(ret.getUpdateTime());
				
				newRet.setRelatedObj(ret.getRelatedObj());
				newRet.setInstDataParentList(ret.getInstDataParentList());
				newRet.setControlParentList(ret.getControlParentList());
				newRet.setChildFreqMap(ret.getChildFreqMap());
				
				this.updateTime(newRet);
				
				MethodNode pointer = (MethodNode) newRet;
				pointer.getRegularState().startTime = pointer.getStartTime();
				pointer.getRegularState().updateTime = pointer.getUpdateTime();
				if (newRet.getInstDataParentList().size() > 0) {
					String pId = newRet.getInstDataParentList().get(0);
					InstNode pNode = this.searchAndGet(pId);
					
					if (pNode != null) {
						//Use a inst data parent to identify frequency
						double curFreq = pNode.getChildFreqMap().get(idxKey);
						pointer.getRegularState().count = (int)curFreq;
					} else {
						logger.error("Null parent when replacing inst. PID " + pId + " CID: " + idxKey);
					}
				}
				
				this.remove(ret);
				this._addInst(idxKey, newRet);
				return newRet;
			} else if ((request == REGULAR) && !(ret.getClass().equals(InstNode.class))) {
				//MethodNode first and then InstNode
				this.updateTime(ret);
				
				MethodNode pointer = (MethodNode) ret;
				RegularState rs = pointer.getRegularState();
				if (rs.startTime == 0L) {
					rs.startTime = pointer.getStartTime();
				}
				rs.updateTime = pointer.getUpdateTime();
				rs.count++;
				
				return ret;
			}
			
			this.updateTime(ret);
			return ret;
		}
		
		InstNode probe = null;
		if (request == REGULAR) {
			probe = new InstNode();
		} else if(request == METHOD) {
			probe = new MethodNode();		
		} else {
			probe = new FieldNode();
		}
		
		//Create new 
		probe.setFromMethod(methodKey);
		probe.setThreadId(threadId);
		probe.setThreadMethodIdx(threadMethodIdx);
		probe.setIdx(idx);
		probe.setOp(BytecodeCategory.getOpcodeObj(opcode));
		probe.setAddInfo(addInfo);
		this.updateTime(probe);
		this._addInst(idxKey, probe);
		return probe;
	}
	
	public InstNode searchAndGet(String idxKey) {
		if (this.instMap.containsKey(idxKey)) {
			return this.instMap.get(idxKey);
		}
		
		if (DEBUG) {			
			logger.warn("Cannot find inst by method key and idx: " +  idxKey);
		}
		
		return null;
	}
	
	public HashMap<String, InstNode> getInstMap() {
		return this.instMap;
	}
	
	@Override
	public boolean add(InstNode inst) {
		String idxKey = StringUtil.genIdxKey(inst.getThreadId(), 
				inst.getThreadMethodIdx(), 
				inst.getIdx());
		return this._addInst(idxKey, inst);
	}
	
	@Override
	public boolean remove(Object o) {
		if (!(o instanceof InstNode)) {			
			logger.error("Non-compatible object type: " + o.getClass());
			return false;
		}
		
		InstNode inst = (InstNode) o;
		String idxKey = StringUtil.genIdxKey(inst.getThreadId(), 
				inst.getThreadMethodIdx(), 
				inst.getIdx());
		return this._removeInst(idxKey, inst);
	}
		
	public static class InstNodeComp implements Comparator<InstNode> {

		@Override
		public int compare(InstNode i1, InstNode i2) {
			String i1Idx = StringUtil.genIdxKey(i1.getThreadId(), i1.getThreadMethodIdx(), i1.getIdx());
			String i2Idx = StringUtil.genIdxKey(i2.getThreadId(), i2.getThreadMethodIdx(), i2.getIdx());
			return i1Idx.compareTo(i2Idx);
		}
		
	}
	
	public static void main(String[] args) {
		InstPool pool = new InstPool();
		InstNode i1 = pool.searchAndGet("a", 
				0, 
				0, 
				1, 
				92, 
				"", 
				InstPool.REGULAR);
		InstNode i2 = pool.searchAndGet("a", 
				0, 
				0, 
				2, 
				92, 
				"", 
				InstPool.REGULAR);
		System.out.println("Pool size: " + pool.size());
	}
}
