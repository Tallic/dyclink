package edu.columbia.psl.cc.datastruct;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import edu.columbia.psl.cc.pojo.FakeVar;
import edu.columbia.psl.cc.pojo.LabelInterval;
import edu.columbia.psl.cc.pojo.LocalVar;
import edu.columbia.psl.cc.pojo.ObjVar;
import edu.columbia.psl.cc.pojo.Var;

public class VarPool extends ArrayList<Var>{
	
	private static final long serialVersionUID = 1L;
	
	private static AtomicInteger fakeIdCreator = new AtomicInteger();
	
	public VarPool() {
		
	}
	
	public VarPool(Collection<Var> input) {
		for (Var v: input) {
			this.add(v);
		}
	}
	
	private static int genFakeId() {
		return fakeIdCreator.getAndIncrement();
	}

	private static ObjVar genObjVar(String className, String methodName, int silId, String varInfo) {
		ObjVar ov = new ObjVar();
		ov.setSilId(silId);
		ov.setClassName(className);
		ov.setMethodName(methodName);
		
		//Parse varInfo
		String[] info = varInfo.split(":");
		ov.setNativeClassName(info[0]);
		ov.setVarName(info[1]);
		return ov;
	}
	
	/**
	 * When generate a local var, don't know its start/end label
	 * Add labels when analyze it
	 * @param className
	 * @param methodName
	 * @param varInfo
	 * @return
	 */
	private static LocalVar genLocalVar(String className, String methodName, int varId) {
		LocalVar lv = new LocalVar();
		lv.setSilId(2);
		lv.setClassName(className);
		lv.setMethodName(methodName);
		lv.setLocalVarId(varId);
		return lv;
	}
	
	private static Var genFakeVar() {
		FakeVar fakeVar = new FakeVar();
		fakeVar.setMethodName("fakeMethod");
		fakeVar.setClassName("fakeClass");
		fakeVar.setSilId(3);
		fakeVar.setFakeId(genFakeId());
		return fakeVar;
	}
	
	private static int rangeSearch(int offset, int startOffset, int endOffset) {
		
		/*System.out.println("Target: " + offset);
		System.out.println("Start: " + startOffset);
		System.out.println("End: " + endOffset);*/
		
		if (offset >= startOffset && offset <= endOffset) {
			return 0;
		} else {
			int startDiff = Math.abs(offset - startOffset);
			int endDiff = Math.abs(offset -endOffset);
			int ret = startDiff <= endDiff? startDiff: endDiff;
			return ret;
		}
	}
		
	public void splitLocalVarWithMultipleLabels() {
		Set<Var> record = new HashSet<Var>();
		Iterator<Var> varIT = this.iterator();
		
		while(varIT.hasNext()) {
			Var v = varIT.next();
			if (!(v instanceof LocalVar))
				continue ;
			
			LocalVar lv = (LocalVar)v;
			if (lv.getIntervals().size() == 1)
				continue;
			
			varIT.remove();
			
			System.out.println("Start to split var: " + lv);
			for (LabelInterval interval: lv.getIntervals()) {
				LocalVar newVar = genLocalVar(lv.getClassName(), lv.getMethodName(), lv.getLocalVarId());
				newVar.addLabelInterval(interval);
				record.add(newVar);
			}
		}
		
		this.addAll(record);
	}
	
	/**
	 * mustExist is for inconsistent label provided by asm
	 * The exact label of the first appearance of a var is a little bit diff from what asm provides
	 * @param className
	 * @param methodName
	 * @param varId
	 * @param offset
	 * @param mustExist
	 * @return
	 */
	public LocalVar retrieveLocalVar(String className, 
			String methodName, 
			int varId, 
			int offset, 
			boolean mustExist) {
		
		int minDiff = Integer.MAX_VALUE;
		LocalVar minLv = null;
		for (Var v: this) {
			if (!(v instanceof LocalVar))
				continue ;
			
			//Getting 0 should work here, since LocalVar should be splitted already
			LocalVar lv = (LocalVar)v;
			LabelInterval interval = lv.getIntervals().get(0);
			if (lv.getClassName().equals(className) && 
					lv.getMethodName().equals(methodName) && 
					lv.getLocalVarId() == varId) {
				int searchResult = rangeSearch(offset, interval.getStartOffset(), interval.getEndOffset());
				if (searchResult == 0) {
					return lv;
				} else {
					if (searchResult < minDiff) {
						minLv = lv;
						minDiff = searchResult;
					}
				}
					
			}
		}
		
		if (mustExist) {
			System.out.println("No perfect match. Label diff: " + minDiff);
			System.out.println("Best Variable: " + minLv);
			return minLv;
		} else {
			System.err.println("Warning find no local var: " + className + " " + methodName + " " + varId + " " + offset);
			return null;
		}
	}
	
	public LocalVar retrieveLocalVarID(String className, String methodName, int varId) {
		for (Var v: this) {
			if (!(v instanceof LocalVar))
				continue ;
			
			LocalVar lv = (LocalVar)v;
			if (lv.getClassName().equals(className) && 
					lv.getMethodName().equals(methodName) && 
					lv.getLocalVarId() == varId) {
				return lv;
			}
		}
		System.err.println("Unable to find suitable local var: " + className + " " + methodName + " " + varId);
		return null;
	}
		
	public Var searchVar(String className, String methodName, int silId, String varInfo) {
		for (Var v: this) {
			if (v.getClassName().equals(className) && 
					v.getMethodName().equals(methodName) && 
					v.getSilId() == silId && 
					v.getVarInfo().equals(varInfo)) {
				return v;
			}
		}
		
		//Reach here means that this var is new
		Var v;
		if (silId == 0 || silId == 1) {
			v = genObjVar(className, methodName, silId, varInfo);
		} else if (silId == 2) {
			v = genLocalVar(className, methodName, Integer.valueOf(varInfo));
		}else {
			v = genFakeVar();
		}
		this.add(v);
		return v;
	}

}
